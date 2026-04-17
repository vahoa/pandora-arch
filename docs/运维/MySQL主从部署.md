# MySQL 9.6.0 主从部署（GTID + 半同步）

> 适用版本：MySQL 9.6.0（2026 最新稳定版）
> 架构：1 主 N 从 · GTID 自动同步 · 半同步复制 · ProxySQL 读写分离（可选）

---

## 一、目标与拓扑

```
┌────────────┐      binlog (GTID)     ┌────────────┐
│  Master    │ ─────────────────────▶ │  Slave #1  │
│  :3306     │                        │  :3307     │
└────────────┘ ─────────────────────▶ ┌────────────┐
                                      │  Slave #2  │
                                      │  :3308     │
                                      └────────────┘
```

- **Master**：写节点，开启 binlog + GTID + semi-sync
- **Slave**：只读节点，通过 I/O + SQL 线程追加 binlog
- 应用侧：`spring.datasource.dynamic` 配置 master / slave 两个数据源，读走 slave

---

## 二、单机多实例部署（推荐学习测试用）

### 2.1 准备目录

```bash
# Linux
sudo mkdir -p /data/mysql/{3306,3307,3308}/{data,conf,logs}
sudo chown -R mysql:mysql /data/mysql
```

Windows 11：

```powershell
mkdir D:\data\mysql\3306\data, D:\data\mysql\3306\conf, D:\data\mysql\3306\logs
mkdir D:\data\mysql\3307\data, D:\data\mysql\3307\conf, D:\data\mysql\3307\logs
mkdir D:\data\mysql\3308\data, D:\data\mysql\3308\conf, D:\data\mysql\3308\logs
```

### 2.2 Master 配置 `my.cnf`

`/data/mysql/3306/conf/my.cnf`：

```ini
[mysqld]
server-id=1
port=3306
datadir=/data/mysql/3306/data
socket=/data/mysql/3306/mysql.sock

# ===== GTID =====
gtid_mode=ON
enforce_gtid_consistency=ON

# ===== Binlog =====
log_bin=/data/mysql/3306/logs/mysql-bin
binlog_format=ROW
binlog_row_image=FULL
binlog_expire_logs_seconds=604800         # 7 天
max_binlog_size=512M
sync_binlog=1

# ===== InnoDB =====
innodb_flush_log_at_trx_commit=1
innodb_buffer_pool_size=2G
innodb_log_file_size=512M

# ===== 半同步（MySQL 9.x 默认内建） =====
plugin_load="rpl_semi_sync_source=semisync_source.so;rpl_semi_sync_replica=semisync_replica.so"
rpl_semi_sync_source_enabled=1
rpl_semi_sync_source_timeout=1000          # 1s 降级为异步
rpl_semi_sync_replica_enabled=1            # 主也开启，以便主从切换

# ===== 性能 =====
default_authentication_plugin=caching_sha2_password
character_set_server=utf8mb4
collation_server=utf8mb4_0900_ai_ci
```

### 2.3 Slave 配置

`/data/mysql/3307/conf/my.cnf`（`server-id=2`，`port=3307`）：

```ini
[mysqld]
server-id=2
port=3307
datadir=/data/mysql/3307/data
socket=/data/mysql/3307/mysql.sock

# 复用 Master 的配置，以下是 Slave 专属
relay_log=/data/mysql/3307/logs/relay-bin
read_only=ON                                # 从库只读
super_read_only=ON                          # 阻止 super 用户写入（保命）
gtid_mode=ON
enforce_gtid_consistency=ON
log_replica_updates=ON
skip_replica_start=OFF

rpl_semi_sync_replica_enabled=1

innodb_buffer_pool_size=2G
default_authentication_plugin=caching_sha2_password
```

Slave #2（`/data/mysql/3308/conf/my.cnf`）：把 `server-id` 改为 `3`，`port` 改为 `3308`，其余同上。

### 2.4 初始化与启动

```bash
# 初始化 3 个实例
for PORT in 3306 3307 3308; do
  mysqld --initialize-insecure \
    --user=mysql \
    --datadir=/data/mysql/$PORT/data
done

# 启动
mysqld --defaults-file=/data/mysql/3306/conf/my.cnf &
mysqld --defaults-file=/data/mysql/3307/conf/my.cnf &
mysqld --defaults-file=/data/mysql/3308/conf/my.cnf &
```

---

## 三、建立复制关系

### 3.1 在 Master 创建复制账号

```sql
-- 登录 Master（默认无密码）
mysql -uroot -P3306 -S /data/mysql/3306/mysql.sock

-- 创建专用复制账号（caching_sha2_password 必须用明文，复制专用不会通过网络传输密码哈希）
CREATE USER 'repl'@'%' IDENTIFIED WITH 'caching_sha2_password' BY 'Repl@2026';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'repl'@'%';

-- 同时建 root 强密码（生产环境必做）
ALTER USER 'root'@'localhost' IDENTIFIED BY 'Root@2026';

FLUSH PRIVILEGES;
```

### 3.2 在 Slave 配置复制源

```sql
-- 登录 Slave 3307
mysql -uroot -P3307 -S /data/mysql/3307/mysql.sock

-- MySQL 9.x 新语法（已废弃 CHANGE MASTER TO）
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='127.0.0.1',
  SOURCE_PORT=3306,
  SOURCE_USER='repl',
  SOURCE_PASSWORD='Repl@2026',
  SOURCE_AUTO_POSITION=1,        -- 使用 GTID 自动定位
  SOURCE_SSL=1,
  GET_SOURCE_PUBLIC_KEY=1;

START REPLICA;

-- 查看状态
SHOW REPLICA STATUS\G
-- 关键字段：
-- Replica_IO_Running: Yes
-- Replica_SQL_Running: Yes
-- Seconds_Behind_Source: 0
-- Auto_Position: 1
```

对 Slave 3308 执行同样的操作。

### 3.3 验证同步

```sql
-- 在 Master 建库建表
CREATE DATABASE pandora;
USE pandora;
CREATE TABLE t_demo (id BIGINT PRIMARY KEY, name VARCHAR(50));
INSERT INTO t_demo VALUES (1, 'hello');

-- Slave 查询应该立刻能看到
mysql -uroot -P3307 -e "SELECT * FROM pandora.t_demo"
```

---

## 四、应用侧（pandora-arch）读写分离

修改 `pandora-start/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    dynamic:
      primary: master
      strict: false
      datasource:
        master:
          url: jdbc:mysql://127.0.0.1:3306/pandora?...
          username: pandora_rw
          password: RwPass@2026
        slave_1:
          url: jdbc:mysql://127.0.0.1:3307/pandora?...
          username: pandora_ro
          password: RoPass@2026
        slave_2:
          url: jdbc:mysql://127.0.0.1:3308/pandora?...
          username: pandora_ro
          password: RoPass@2026
```

Service 层读写分离：

```java
@Service
public class UserQueryService {

    // 读走从库负载均衡
    @DS("slave")
    public List<User> list() { ... }

    // 事务写入自动回主库
    @DS("master")
    @Transactional
    public void save(User u) { ... }
}
```

`dynamic-datasource` 4.3 支持 `@DS("slave")` 时自动在 `slave_1 / slave_2` 中负载均衡。

---

## 五、监控与容灾要点

| 指标 | 查询 SQL | 告警阈值 |
|------|---------|---------|
| 主从延迟 | `SHOW REPLICA STATUS\G`（`Seconds_Behind_Source`） | > 5s |
| 复制通道 | `Replica_IO_Running` / `Replica_SQL_Running` | 任一 != Yes |
| 半同步状态 | `SHOW GLOBAL STATUS LIKE 'Rpl_semi_sync_%'` | `*_status` = OFF |
| InnoDB 死锁 | `SHOW ENGINE INNODB STATUS` | 频繁出现 |

生产环境推荐：

- **MHA / Orchestrator**：主库故障自动切换
- **ProxySQL**：SQL 层面的读写分离 + 故障探测
- **MySQL Group Replication (MGR)** 或 **InnoDB Cluster**：多主高可用
- **Percona XtraBackup**：在线物理备份

---

## 六、常见坑

1. **`server-id` 冲突** → 3 个实例必须不同。
2. **GTID 模式下手动写入 binlog** → Slave 直接报错，只能跳过或重置。
3. **防火墙 / Docker 端口未映射** → `SHOW REPLICA STATUS` 显示 `connecting`。
4. **`caching_sha2_password` 连接失败** → JDBC URL 必须加 `allowPublicKeyRetrieval=true` 或显式下发 server 公钥。
5. **Slave 写入** → 一定要 `read_only=ON` + `super_read_only=ON`，否则数据不一致后同步会断裂。
