# 快速开始

> 工程：`pandora-arch` · JDK 25 + Spring Boot 4.0.5 + Spring Cloud Alibaba 2025.1.0.0 + DDD

## 环境要求

| 组件 | 版本 | 必须？ |
|------|------|-------|
| JDK | **25**（LTS） | 必须 |
| Maven | 3.9+ | 必须 |
| MySQL | 8.x / 9.x（推荐 9.x，驱动 `mysql-connector-j` 9.6.0） | 必须 |
| Redis | 7.x | 必须（Redisson 4.3.1 客户端） |
| MinIO | 最新版 | 可选（文件存储） |

微服务模式额外依赖：

| 组件 | 版本 | 必须？ |
|------|------|-------|
| Nacos | 2.x（项目默认指向 3 节点集群） | 必须 |
| Sentinel Dashboard | 1.8.x | 可选 |
| Kafka | 3.8.x | 可选（`pandora.kafka.enabled=true` 才启用） |
| MongoDB | 5.x / 6.x / 7.x | 可选（`pandora.mongodb.enabled=true` 才启用） |

> **Nacos 默认集群**：`192.168.50.168:8140,192.168.50.168:8550,192.168.50.168:8960`，可通过 `NACOS_SERVER_ADDR` 覆盖。

## 初始化数据库

```bash
mysql -u root -p123456 < docs/sql/schema.sql
mysql -u root -p123456 < docs/sql/schema-auth.sql
# 如有 RBAC / Member 扩展脚本：
# mysql -u root -p123456 ddd_platform < docs/sql/schema-rbac.sql
# mysql -u root -p123456 ddd_platform < docs/sql/schema-member.sql
```

---

## 模式一：单体部署

### 1. 启动授权服务器（端口 9100）

```bash
cd pandora-auth
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

启动类：`cn.pandora.auth.AuthServerApplication`，业务端口 **9100**，运维端口 **19100**。

### 2. 启动单体主应用（端口 8080）

```bash
cd pandora-start
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

启动类：`cn.pandora.PandoraApplication`，业务端口 **8080**，运维端口 **18080**。

### 3. 访问接口文档

- Swagger UI：http://localhost:8080/swagger-ui/index.html
- OpenAPI 3：http://localhost:8080/v3/api-docs
- 健康检查：http://localhost:18080/actuator/health

### 4. 测试接口

```bash
# B 端用户名密码登录（内置 admin/admin123）
curl -X POST "http://localhost:9100/auth/admin/login" \
  -H "Content-Type: application/json" \
  -d '{"loginType":"USERNAME_PASSWORD","username":"admin","password":"admin123"}'

# 使用返回的 accessToken 调用业务接口
curl -H "Authorization: Bearer <access_token>" \
  http://localhost:8080/api/users/1
```

---

## 模式二：微服务部署

### 1. 启动基础设施

- MySQL、Redis、MinIO、Nacos 集群（或单机）
- 可选：Sentinel Dashboard、Kafka、MongoDB

Nacos 控制台：http://<nacos-host>:<port>/nacos（默认账号 nacos / nacos）。

> 本地开发若连不到内网的 Nacos 集群，可设置 `NACOS_SERVER_ADDR=127.0.0.1:8848` 启动本地单机版。

### 2. 启动授权服务器（端口 9100，注册到 Nacos）

```bash
cd pandora-auth
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev --NACOS_ENABLED=true"
```

Nacos 服务名：`pandora-auth-server`，Group：`dev`（即 `spring.profiles.active`）。

### 3. 启动用户微服务（端口 8081）

```bash
cd pandora-service/pandora-service-user
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

Nacos 服务名：`pandora-service-user`，运维端口 **18082**。

### 4. 启动 API 网关（端口 8888）

```bash
cd pandora-gateway
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

Nacos 服务名：`pandora-gateway`，业务端口 **8888**，运维端口 **18888**，基于 Spring Cloud Gateway Server WebFlux。

### 5. 通过网关访问

```bash
# 通过网关登录
curl -X POST http://localhost:8888/auth/admin/login \
  -H "Content-Type: application/json" \
  -d '{"loginType":"USERNAME_PASSWORD","username":"admin","password":"admin123"}'

# 通过网关访问用户服务
curl -H "Authorization: Bearer <access_token>" \
  http://localhost:8888/api/users/1
```

---

## 构建命令

```bash
# 全量构建（默认 dev profile）
mvn clean package -DskipTests

# 指定环境打包
mvn clean package -P dev  -DskipTests
mvn clean package -P test -DskipTests
mvn clean package -P prod -DskipTests

# 仅构建单体
mvn clean package -pl pandora-start -am -DskipTests

# 仅构建用户微服务
mvn clean package -pl pandora-service/pandora-service-user -am -DskipTests

# 仅构建网关
mvn clean package -pl pandora-gateway -am -DskipTests
```

构建产物：

```
pandora-auth/target/pandora-auth-1.0.0.jar
pandora-start/target/pandora-start-1.0.0.jar
pandora-gateway/target/pandora-gateway-1.0.0.jar
pandora-service/pandora-service-user/target/pandora-service-user-1.0.0.jar
```

## JDK 25 注意事项

- 打包插件已为 Lombok 打开 `jdk.compiler` 模块（`--add-opens`），消除 `sun.misc.Unsafe` 警告
- Lombok 锁定到 **1.18.44**，更早版本在 JDK 25 下注解处理会失败
- Spring Framework 7.0 已移除 `spring-jcl`，父 POM 统一兜底引入 `commons-logging`

---

> **作者**：vahoa  
> **日期**：2026 年  
> **作品**：pandora-arch · DDD 架构底座  
> **版权**：© 2026 vahoa. All rights reserved.
