# pandora-arch · DDD 架构底座

基于 **JDK 25 + Spring Boot 4.0.5 + Spring Cloud Alibaba 2025.1.0.0 + DDD（领域驱动设计）** 的多模块 Maven 架构底座。
同时支持 **单体部署** 与 **微服务部署** 两种模式。

## 技术基线

| 维度 | 选型 |
|------|------|
| 运行时 | JDK 25（LTS，默认开启虚拟线程 + Generational ZGC） |
| 核心框架 | Spring Boot 4.0.5（继承自 `spring-boot-starter-parent`）/ Spring Framework 7.x |
| 认证授权 | Spring Security 7.x + Spring Authorization Server 7.0.4 + JJWT 0.12.6 + JustAuth 1.16.7 |
| 微服务 | Spring Cloud 2025.1.0（Oakwood，仅网关）+ Spring Cloud Alibaba 2025.1.0.0（Nacos / Sentinel） |
| 网关 | Spring Cloud Gateway Server WebFlux（新 artifactId：`spring-cloud-starter-gateway-server-webflux`，属性前缀 `spring.cloud.gateway.server.webflux.*`） |
| ORM | **MyBatis-Flex 1.11.6**（`mybatis-flex-spring-boot4-starter`）+ 可选 Spring Data JPA |
| 多数据源 | `dynamic-datasource-spring-boot4-starter` 4.5.0 |
| 数据库 | MySQL 9.x（`mysql-connector-j` 9.6.0），可选 MongoDB 5/6/7（`mongodb-driver-sync` 5.6.4） |
| 缓存 / 锁 | **Redisson 4.3.1**（`redisson-spring-boot-starter` + `redisson-spring-data-40`，不使用 Lettuce/Jedis） |
| 消息 | Spring Kafka 4.0.1 / Kafka Clients 3.8.1（KRaft） |
| 对象存储 | MinIO Java SDK 8.5.17 |
| AI | Spring AI 1.0.0（`spring-ai-starter-model-openai`） |
| API 文档 | SpringDoc OpenAPI 2.7.0 |
| 工具链 | Lombok 1.18.44（JDK 25 兼容版本）、MapStruct 1.6.3、Hutool 5.8.35 |

> **服务间调用**：项目基于 Spring Cloud Alibaba，**原生支持 OpenFeign**（`spring-cloud-starter-openfeign`，配合 `loadbalancer` 做负载均衡与 `sentinel` 做熔断降级）。也可根据场景选用 `RestClient + Nacos NamingService` 或 Dubbo。
> **Nacos Group**：按 `dev/test/prod` 环境命名，不使用 `DEFAULT_GROUP`。

## 模块结构

```
pandora-arch/
├── pandora-common/                    # 公共模块：统一响应、异常体系、LoginUser、注解
├── pandora-common-cloud/              # 微服务公共模块：Nacos、Sentinel、LB 适配
├── pandora-domain/                    # 领域层：聚合根、值对象、领域事件、仓储接口
├── pandora-application/               # 应用层：应用服务、Command、DTO
├── pandora-infrastructure/            # 基础设施层：MyBatis-Flex、Redisson、Kafka、MinIO、Spring AI、Security
├── pandora-api/                       # 接口层：REST 控制器、全局异常、SpringDoc
├── pandora-auth/                      # 授权服务器（独立部署，端口 9100/19100）
├── pandora-start/                     # 单体启动模块（端口 8080/18080）
├── pandora-gateway/                   # API 网关（端口 8888/18888，Gateway Server WebFlux）
└── pandora-service/                   # 微服务集合
    └── pandora-service-user/          # 用户微服务（端口 8081/18082）
```

## 端口规划

| 服务 | 业务端口 | 运维端口 | Nacos 服务名 |
|------|---------|---------|-------------|
| `pandora-start`（单体） | 8080 | 18080 | —（默认不注册） |
| `pandora-auth-server` | 9100 | 19100 | `pandora-auth-server` |
| `pandora-gateway` | 8888 | 18888 | `pandora-gateway` |
| `pandora-service-user` | 8081 | 18082 | `pandora-service-user` |

## 快速开始

```bash
# 1. 初始化数据库（MySQL 8/9）
mysql -u root -p123456 < docs/sql/schema.sql
mysql -u root -p123456 < docs/sql/schema-auth.sql

# 2. 构建
mvn clean package -DskipTests

# 3. 启动授权服务器（端口 9100）
cd pandora-auth && mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"

# 4. 启动单体主应用（端口 8080）
cd pandora-start && mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"

# 5. 访问 Swagger
# http://localhost:8080/swagger-ui/index.html
```

微服务模式请参阅 [快速开始](docs/getting-started.md) 与 [微服务分布式部署指南](docs/微服务分布式部署指南.md)。

## 文档目录

### 核心文档（中文）

| 文档 | 说明 |
|------|------|
| [架构模块详解](docs/架构模块详解.md) | 每个模块的意义、作用、用途、用法、依赖关系及配置说明 |
| [模块组件详解](docs/模块组件详解.md) | 关键类、配置、扩展点的完整参考 |
| [架构设计方案](docs/架构设计方案.md) | 架构定位、技术栈、DDD 四层、安全与部署 |
| [DDD领域驱动设计说明](docs/DDD领域驱动设计说明.md) | DDD 四层架构在本项目中的具体设计与实现 |
| [多数据源与多ORM使用指南](docs/多数据源与多ORM使用指南.md) | dynamic-datasource 4.5.0 + MyBatis-Flex / JPA 共存 |
| [Spring AI 集成指南](docs/Spring%20AI集成指南.md) | Spring AI 1.0.0 集成、API 接口、扩展指南 |
| [MinIO 文件存储接口文档](docs/MinIO文件存储接口文档.md) | MinIO Java SDK 8.5.17 全量 REST 接口与示例 |
| [单体服务部署指南](docs/单体服务部署指南.md) | 单体模式完整配置、启动步骤、认证测试、生产部署 |
| [微服务分布式部署指南](docs/微服务分布式部署指南.md) | Nacos / Gateway / Sentinel 配置与 Docker 部署 |
| [服务启动顺序与配置指南](docs/服务启动顺序与配置指南.md) | 单体与微服务两种模式下的启动顺序与配置诀窍 |
| [服务部署指南](docs/服务部署指南.md) | 生产环境部署、运维、JDK 25 调优 |
| [完整部署操作手册](docs/完整部署操作手册.md) | **从零到启动全部服务**：中间件、配置、启动顺序、排错 |
| [技术栈升级说明](docs/技术栈升级说明.md) | 由旧版升级至 JDK 25 / Spring Boot 4 / MyBatis-Flex 的记录 |

### 基础文档

| 文档 | 说明 |
|------|------|
| [架构设计说明](docs/architecture.md) | 技术栈、模块职责、架构图、依赖关系、端口规划 |
| [快速开始](docs/getting-started.md) | 环境要求、单体部署、微服务部署、构建命令 |
| [OAuth2 认证授权指南](docs/oauth2-guide.md) | 认证流程、业务登录 / 标准 OAuth2、Token 规范 |
| [微服务指南](docs/cloud-guide.md) | Nacos、Sentinel、Gateway Server WebFlux、RestClient 调用 |
| [DDD 分层规范](docs/ddd-specification.md) | 各层规范、代码示例、扩展新限界上下文指南 |
| [数据库脚本](docs/sql/schema.sql) | 数据库初始化 SQL |

## JDK 25 注意事项

- Lombok 锁定 **1.18.44**（更早版本在 JDK 25 下注解处理失败）。
- 打包插件对编译器模块统一添加 `--add-opens`，消除 `sun.misc.Unsafe` 警告。
- Spring Framework 7.0 已移除 `spring-jcl`，父 POM 兜底引入 `commons-logging`。
- 推荐 JVM 参数：`-XX:+UseZGC -XX:+ZGenerational -Xms512m -Xmx2g -Dspring.threads.virtual.enabled=true`。

---

> **作者**：vahoa  
> **日期**：2026 年  
> **作品**：pandora-arch · DDD 架构底座  
> **版权**：© 2026 vahoa. All rights reserved.
