# Redis 7.4.x Cluster 集群部署（三主三从）

> 适用版本：Redis 7.4.2（2026 年最新稳定版）· Redisson 3.50
> 架构：3 主 3 从 · 16384 slot 自动分片 · 客户端直连 / 哨兵 / 集群模式

---

## 一、目标拓扑

```
┌──────────┐  ┌──────────┐  ┌──────────┐
│ Master 1 │  │ Master 2 │  │ Master 3 │
│   7001   │  │   7002   │  │   7003   │
└────┬─────┘  └────┬─────┘  └────┬─────┘
     │ replicate   │ replicate   │ replicate
┌────┴─────┐  ┌────┴─────┐  ┌────┴─────┐
│ Slave 1  │  │ Slave 2  │  │ Slave 3  │
│   7004   │  │   7005   │  │   7006   │
└──────────┘  └──────────┘  └──────────┘
```

- **三主**：分片承担 16384 个 slot（0-5460 / 5461-10922 / 10923-16383）
- **三从**：异步复制对应主节点；主挂掉自动 failover
- **客户端**：MOVED/ASK 重定向，Redisson 在客户端自动处理

---

## 二、单机多端口部署（Linux / Mac）

### 2.1 准备目录

```bash
mkdir -p /data/redis-cluster/{7001,7002,7003,7004,7005,7006}/{data,conf,logs}
```

### 2.2 统一 `redis.conf` 模板

`/data/redis-cluster/7001/conf/redis.conf`（其它节点只需改端口号）：

```conf
port 7001
bind 0.0.0.0
protected-mode yes
requirepass RedisPwd@2026
masterauth RedisPwd@2026

dir /data/redis-cluster/7001/data
logfile /data/redis-cluster/7001/logs/redis.log
pidfile /data/redis-cluster/7001/redis.pid

daemonize yes

# ===== Cluster 核心 =====
cluster-enabled yes
cluster-config-file nodes-7001.conf
cluster-node-timeout 5000
cluster-require-full-coverage no
cluster-allow-reads-when-down no

# ===== 持久化（AOF + RDB 双保险） =====
appendonly yes
appendfsync everysec
aof-use-rdb-preamble yes
save 900 1
save 300 10
save 60 10000

# ===== 内存淘汰 =====
maxmemory 2gb
maxmemory-policy allkeys-lru

# ===== 性能 =====
tcp-keepalive 60
io-threads 4
io-threads-do-reads yes
```

批量生成：

```bash
for PORT in 7001 7002 7003 7004 7005 7006; do
  cp /data/redis-cluster/7001/conf/redis.conf /data/redis-cluster/$PORT/conf/redis.conf
  sed -i "s/7001/$PORT/g" /data/redis-cluster/$PORT/conf/redis.conf
done
```

### 2.3 启动所有节点

```bash
for PORT in 7001 7002 7003 7004 7005 7006; do
  redis-server /data/redis-cluster/$PORT/conf/redis.conf
done

# 验证
ps -ef | grep redis-server
netstat -tlnp | grep 700
```

### 2.4 创建集群

```bash
redis-cli --cluster create \
  127.0.0.1:7001 127.0.0.1:7002 127.0.0.1:7003 \
  127.0.0.1:7004 127.0.0.1:7005 127.0.0.1:7006 \
  --cluster-replicas 1 \
  -a RedisPwd@2026
```

提示 `Can I set the above configuration? (type 'yes' to accept)` → 输入 `yes`。

成功日志示例：

```
>>> Performing Cluster Check (using node 127.0.0.1:7001)
[OK] All nodes agree about slots configuration.
[OK] All 16384 slots covered.
```

### 2.5 验证

```bash
# -c 启用集群模式（自动跟随 MOVED）
redis-cli -c -p 7001 -a RedisPwd@2026

127.0.0.1:7001> CLUSTER INFO
cluster_state:ok
cluster_slots_assigned:16384
cluster_slots_ok:16384
cluster_known_nodes:6
cluster_size:3

127.0.0.1:7001> CLUSTER NODES
xxx  127.0.0.1:7001@17001 myself,master - 0 0 1 connected 0-5460
...

127.0.0.1:7001> SET user:1 alice
-> Redirected to slot [14687] located at 127.0.0.1:7003
OK
```

---

## 三、Docker Compose 一键部署（推荐）

`docker-compose-redis-cluster.yml`：

```yaml
version: "3.9"
x-redis-common: &redis-common
  image: redis:7.4.2
  restart: always
  command: >
    redis-server
    --cluster-enabled yes
    --cluster-require-full-coverage no
    --cluster-node-timeout 5000
    --appendonly yes
    --requirepass RedisPwd@2026
    --masterauth RedisPwd@2026

services:
  r1: { <<: *redis-common, container_name: r1, ports: ["7001:6379", "17001:16379"] }
  r2: { <<: *redis-common, container_name: r2, ports: ["7002:6379", "17002:16379"] }
  r3: { <<: *redis-common, container_name: r3, ports: ["7003:6379", "17003:16379"] }
  r4: { <<: *redis-common, container_name: r4, ports: ["7004:6379", "17004:16379"] }
  r5: { <<: *redis-common, container_name: r5, ports: ["7005:6379", "17005:16379"] }
  r6: { <<: *redis-common, container_name: r6, ports: ["7006:6379", "17006:16379"] }

  cluster-init:
    image: redis:7.4.2
    depends_on: [r1, r2, r3, r4, r5, r6]
    command: >
      sh -c "sleep 10 && echo yes | redis-cli --cluster create
             r1:6379 r2:6379 r3:6379 r4:6379 r5:6379 r6:6379
             --cluster-replicas 1 -a RedisPwd@2026"
```

启动：`docker compose -f docker-compose-redis-cluster.yml up -d`

---

## 四、Redisson（pandora-arch 默认客户端）配置

`application.yml`：

```yaml
spring:
  redis:
    redisson:
      config: |
        clusterServersConfig:
          nodeAddresses:
            - "redis://127.0.0.1:7001"
            - "redis://127.0.0.1:7002"
            - "redis://127.0.0.1:7003"
            - "redis://127.0.0.1:7004"
            - "redis://127.0.0.1:7005"
            - "redis://127.0.0.1:7006"
          password: "RedisPwd@2026"
          scanInterval: 2000
          masterConnectionPoolSize: 32
          slaveConnectionPoolSize: 32
          readMode: SLAVE
          subscriptionMode: MASTER
          retryAttempts: 3
          retryInterval: 1500
        threads: 16
        nettyThreads: 32
        codec: !<org.redisson.codec.JsonJacksonCodec> {}
```

使用示例（分布式锁 + 限流 + 缓存）：

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final DistributedLockService lockService;
    private final RedissonClient redissonClient;

    public void pay(Long orderId) {
        lockService.runWithLock("order:pay:" + orderId, 3, 10, () -> {
            // 业务逻辑
        });
    }

    public boolean tryAccess(String ip) {
        RRateLimiter limiter = redissonClient.getRateLimiter("rate:ip:" + ip);
        limiter.trySetRate(RateType.OVERALL, 100, 1, RateIntervalUnit.SECONDS);
        return limiter.tryAcquire();
    }
}
```

---

## 五、扩缩容

### 5.1 加节点

```bash
# 新起 7007 / 7008
redis-server --port 7007 --cluster-enabled yes --appendonly yes --daemonize yes
redis-server --port 7008 --cluster-enabled yes --appendonly yes --daemonize yes

# 加入集群
redis-cli --cluster add-node 127.0.0.1:7007 127.0.0.1:7001 -a RedisPwd@2026
redis-cli --cluster add-node 127.0.0.1:7008 127.0.0.1:7001 --cluster-slave --cluster-master-id <7007-id> -a RedisPwd@2026

# 重新分片
redis-cli --cluster reshard 127.0.0.1:7001 -a RedisPwd@2026
```

### 5.2 下线节点

```bash
# 转移 slot 后删除
redis-cli --cluster del-node 127.0.0.1:7001 <node-id> -a RedisPwd@2026
```

---

## 六、生产环境清单

| 要点 | 配置 |
|------|------|
| 持久化 | AOF + RDB 双开启 |
| 内存 | `maxmemory` 不超过机器内存 60% |
| 淘汰策略 | `allkeys-lru` 或 `volatile-ttl` |
| 客户端超时 | 建议 3s，避免单点拖垮应用 |
| 监控 | Prometheus + redis_exporter + Grafana Dashboard |
| 日志 | `logfile`，按天切割 |
| 安全 | `requirepass` + `bind` + 防火墙白名单 |
| 备份 | 每天 AOF 离线归档到对象存储 |
| 大 key | 慢查询 + `redis-cli --bigkeys` 定期巡检 |
| 热 key | `MONITOR` / `OBJECT FREQ` / 客户端本地二级缓存 |
