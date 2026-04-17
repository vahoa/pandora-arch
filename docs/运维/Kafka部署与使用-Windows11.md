# Kafka 3.8.x on Windows 11 部署与使用

> 适用版本：Apache Kafka 3.8.1（KRaft 模式，无 ZooKeeper）· Spring Kafka 4.0.1
> 目标：Windows 11 本地单节点 + 伪分布式三节点，含 pandora-arch 端收发示例

---

## 一、前置条件

| 组件 | 要求 |
|------|------|
| 操作系统 | Windows 11（21H2+） |
| JDK | JDK 25（Kafka 3.8 兼容 JDK 17/21/25） |
| 磁盘 | ≥ 20 GB（日志 + 数据分区） |
| 内存 | ≥ 8 GB |
| 端口 | 9092 / 9093 / 9094（Broker）· 9101（JMX） |

验证 JDK：

```powershell
java -version   # 应看到 "25" 字样
$env:JAVA_HOME  # 必须设置，且不含空格；空格会导致 Kafka 脚本异常
```

> 若 `JAVA_HOME` 含空格（如 `C:\Program Files\Java\jdk-25`），建议用 `mklink` 建一个符号链接到无空格路径，或切换到 `D:\Java\jdk-25`。

---

## 二、单节点快速启动（KRaft 模式）

### 2.1 下载与解压

```powershell
# 下载二进制包（Apache 镜像）
Invoke-WebRequest "https://downloads.apache.org/kafka/3.8.1/kafka_2.13-3.8.1.tgz" `
  -OutFile "$env:USERPROFILE\Downloads\kafka-3.8.1.tgz"

# 解压到 D:\kafka
mkdir D:\kafka
tar -xzf "$env:USERPROFILE\Downloads\kafka-3.8.1.tgz" -C D:\kafka
cd D:\kafka\kafka_2.13-3.8.1
```

### 2.2 生成 Cluster ID

```powershell
# 生成唯一集群 ID（KRaft 模式必需）
$CLUSTER_ID = .\bin\windows\kafka-storage.bat random-uuid
Write-Host "Cluster ID: $CLUSTER_ID"
```

### 2.3 修改 KRaft 配置 `config\kraft\server.properties`

```properties
process.roles=broker,controller
node.id=1
controller.quorum.voters=1@localhost:9093

listeners=PLAINTEXT://:9092,CONTROLLER://:9093
inter.broker.listener.name=PLAINTEXT
advertised.listeners=PLAINTEXT://localhost:9092
controller.listener.names=CONTROLLER
listener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,SSL:SSL,SASL_PLAINTEXT:SASL_PLAINTEXT,SASL_SSL:SASL_SSL

num.network.threads=3
num.io.threads=8
socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600

log.dirs=D:/kafka/data/kraft
num.partitions=3
num.recovery.threads.per.data.dir=1
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
log.retention.hours=168
log.retention.bytes=1073741824
log.segment.bytes=536870912
log.retention.check.interval.ms=300000

auto.create.topics.enable=false
```

### 2.4 格式化 & 启动

```powershell
# 格式化元数据目录（只做一次）
.\bin\windows\kafka-storage.bat format -t $CLUSTER_ID -c .\config\kraft\server.properties

# 启动 Broker（前台运行）
.\bin\windows\kafka-server-start.bat .\config\kraft\server.properties
```

启动成功日志：

```
[KafkaServer id=1] started (kafka.server.KafkaServer)
```

开新 PowerShell 窗口做验证：

```powershell
cd D:\kafka\kafka_2.13-3.8.1

# 建 Topic
.\bin\windows\kafka-topics.bat --create --topic pandora.test --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

# 列出
.\bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092

# 生产消息（回车换一条）
.\bin\windows\kafka-console-producer.bat --topic pandora.test --bootstrap-server localhost:9092
> hello pandora
> kafka on win11

# 另一窗口消费
.\bin\windows\kafka-console-consumer.bat --topic pandora.test --from-beginning --bootstrap-server localhost:9092
```

---

## 三、伪分布式三 Broker（单机三进程）

### 3.1 复制三份配置

`config\kraft\broker1.properties` / `broker2.properties` / `broker3.properties`：

| 项 | Broker1 | Broker2 | Broker3 |
|----|---------|---------|---------|
| `node.id` | 1 | 2 | 3 |
| `listeners` | `PLAINTEXT://:9092,CONTROLLER://:9192` | `PLAINTEXT://:9093,CONTROLLER://:9193` | `PLAINTEXT://:9094,CONTROLLER://:9194` |
| `advertised.listeners` | `PLAINTEXT://localhost:9092` | `PLAINTEXT://localhost:9093` | `PLAINTEXT://localhost:9094` |
| `log.dirs` | `D:/kafka/data/b1` | `D:/kafka/data/b2` | `D:/kafka/data/b3` |
| `controller.quorum.voters` | `1@localhost:9192,2@localhost:9193,3@localhost:9194` | 同左 | 同左 |

其余 `process.roles=broker,controller` 和公共参数完全相同。

### 3.2 启动脚本 `start-kafka-cluster.ps1`

```powershell
$CLUSTER_ID = "GPDCNOo6R-O5wCzbwvGWGA"  # 用 kafka-storage random-uuid 生成一次后固定

foreach ($id in 1..3) {
    $cfg = ".\config\kraft\broker$id.properties"
    if (-not (Test-Path "D:\kafka\data\b$id\meta.properties")) {
        .\bin\windows\kafka-storage.bat format -t $CLUSTER_ID -c $cfg
    }
    Start-Process powershell -ArgumentList "-NoExit", "-Command", `
        "cd D:\kafka\kafka_2.13-3.8.1; .\bin\windows\kafka-server-start.bat $cfg"
}
```

运行：`.\start-kafka-cluster.ps1` → 弹出 3 个窗口分别跑 3 个 Broker。

---

## 四、Windows 服务化（生产部署）

### 4.1 使用 NSSM（Non-Sucking Service Manager）

```powershell
# 下载 NSSM
choco install nssm -y

# 注册 Kafka 服务
nssm install kafka-broker1 `
  "D:\kafka\kafka_2.13-3.8.1\bin\windows\kafka-server-start.bat" `
  "D:\kafka\kafka_2.13-3.8.1\config\kraft\broker1.properties"

nssm set kafka-broker1 AppStdout "D:\kafka\logs\broker1.log"
nssm set kafka-broker1 AppStderr "D:\kafka\logs\broker1.err"
nssm set kafka-broker1 Start SERVICE_AUTO_START

nssm start kafka-broker1
```

### 4.2 服务管理

```powershell
Get-Service kafka-*
Restart-Service kafka-broker1
```

---

## 五、pandora-arch 侧收发示例

### 5.1 启用 Kafka（`application.yml`）

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092,localhost:9093,localhost:9094
    consumer:
      group-id: pandora-group
pandora:
  kafka:
    enabled: true     # 触发 KafkaConfig @ConditionalOnProperty
```

### 5.2 生产

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/demo/kafka")
public class KafkaDemoController {

    private final KafkaProducerService producer;

    @PostMapping("/send")
    public Result<Void> send(@RequestParam String msg) {
        producer.sendAsync("pandora.test", "k-" + System.currentTimeMillis(), msg);
        return Result.success();
    }
}
```

### 5.3 消费

```java
@Component
@Slf4j
public class OrderKafkaListener {

    @KafkaListener(topics = "pandora.test", containerFactory = "kafkaListenerContainerFactory")
    public void onMessage(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            log.info("收到消息 partition={} offset={} value={}",
                    record.partition(), record.offset(), record.value());
            // 业务处理
            ack.acknowledge();                 // 手动提交 offset
        } catch (Exception e) {
            log.error("消费失败，不 ack，下次重新投递", e);
        }
    }
}
```

---

## 六、常见问题

| 现象 | 原因 | 解决 |
|------|------|------|
| `Error: Cluster ID mismatch` | 多次 `format` 用了不同 UUID | 删除 `log.dirs` 目录后重新格式化 |
| Windows 脚本卡死、乱码 | 控制台编码非 UTF-8 | `chcp 65001` 切 UTF-8 再启动 |
| `JAVA_HOME not set` | 环境变量未配置 | `setx JAVA_HOME "D:\Java\jdk-25" /M` |
| 消费者反复收到同一条消息 | 业务异常未 ack 且无限重试 | 配置 `ErrorHandler` + DLQ（死信主题） |
| 远程连不上 | `advertised.listeners` 为 localhost | 改成公网 IP 或主机名并开放防火墙 |
| Broker 启动慢 | 日志目录机械盘 | 换 SSD；必要时调 `num.recovery.threads.per.data.dir` |

---

## 七、可视化 & 运维工具

- **Kafka UI (provectuslabs)**：Web 管理面板 — `docker run -p 8080:8080 provectuslabs/kafka-ui`
- **Redpanda Console**：同系列，界面更现代
- **Offset Explorer**：桌面客户端，Windows 友好
- **Prometheus + Kafka Exporter + Grafana**：指标监控
- **Burrow**（LinkedIn 出品）：消费者 lag 告警
