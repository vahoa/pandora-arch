# 架构设计说明

> 工程：`pandora-arch` · GroupId：`cn.pandora` · 版本：`1.0.0`
> 基准日期：2026-04 · 基线：JDK 25 + Spring Boot 4.0.5 + Spring Cloud Alibaba 2025.1.0.0 + DDD

## 技术栈

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 运行时 | JDK | **25** | LTS，虚拟线程默认启用、ZGC Generational |
| 核心框架 | Spring Boot | **4.0.5** | 父 POM 继承 `spring-boot-starter-parent` |
| 核心框架 | Spring Framework | 由 Boot BOM 管理（≥ 7.0.2） | 严禁手动声明 |
| 安全 | Spring Security | 由 Boot BOM 管理（7.x） | 资源服务器、方法级鉴权 |
| 授权 | Spring Authorization Server | **7.0.4** | OAuth2 授权服务器 |
| 微服务 | Spring Cloud | **2025.1.0**（Oakwood） | **仅 `pandora-gateway` 使用**（gateway BOM） |
| 微服务 | Spring Cloud Alibaba | **2025.1.0.0** | Nacos 注册/配置、Sentinel 流控 |
| 网关 | Spring Cloud Gateway Server WebFlux | 来自 SC BOM | 新 artifactId：`spring-cloud-starter-gateway-server-webflux`，新属性前缀 `spring.cloud.gateway.server.webflux.*` |
| ORM | **MyBatis-Flex** | **1.11.6** | 项目强制使用 `mybatis-flex-spring-boot4-starter`，**严禁引入 MyBatis-Plus** |
| 多数据源 | dynamic-datasource | **4.5.0** | Spring Boot 4 专用 `dynamic-datasource-spring-boot4-starter` |
| ORM（辅） | Spring Data JPA | 由 Boot BOM 管理 | 与 MyBatis-Flex 多 ORM 共存 |
| 连接池 | HikariCP | 6.3.0 | 由 Boot BOM 锁定 |
| 数据库驱动 | mysql-connector-j | **9.6.0** | MySQL 9 协议 |
| 缓存 / 锁 | Redisson | **4.3.1** | 统一使用 `redisson-spring-boot-starter` + `redisson-spring-data-40`，**严禁使用 Lettuce/Jedis** |
| 文档库 | MongoDB Driver | **5.6.4** | `mongodb-driver-sync` |
| 消息 | Spring Kafka | **4.0.1** | `spring-kafka` |
| 消息 | Kafka Clients | **3.8.1** | KRaft |
| 对象存储 | MinIO Java SDK | **8.5.17** | S3 兼容 |
| API 文档 | SpringDoc OpenAPI | **2.7.0** | OpenAPI 3.1 |
| 代码增强 | Lombok | **1.18.44** | JDK 25 兼容 |
| 对象映射 | MapStruct | **1.6.3** | 与 Lombok 联用 |
| 工具 | Hutool | **5.8.35** | `hutool-all` |
| JWT | JJWT | **0.12.6** | `jjwt-api/impl/jackson` |
| 社交登录 | JustAuth | **1.16.7** | 微信/QQ/企业微信 |
| AI | Spring AI | **1.0.0** | `spring-ai-starter-model-openai`（可选） |

> 依赖版本由根 `pom.xml` `dependencyManagement` 统一锁定；子模块一律不指定版本。

## 模块结构

```
pandora-arch/                             # 父 POM（统一依赖版本管理）
│
├── ===== 基础层 =====
├── pandora-common/                       # 公共模块：Result、异常、工具、安全上下文、注解
├── pandora-common-cloud/                 # 微服务公共模块：Nacos + Sentinel（仅此模块和网关允许直接用 SCA）
│
├── ===== DDD 分层 =====
├── pandora-domain/                       # 领域层：聚合根、值对象、领域事件、仓储接口
├── pandora-application/                  # 应用层：应用服务、Command/Query、DTO、Assembler
├── pandora-infrastructure/               # 基础设施层：持久化、缓存、锁、MQ、Minio、AI、安全
├── pandora-api/                          # 接口层：REST 控制器、全局异常、Swagger
│
├── ===== 认证授权 =====
├── pandora-auth/                         # OAuth2 授权服务器（Spring Authorization Server 7.0.4）
│
├── ===== 单体模式 =====
├── pandora-start/                        # 单体启动模块：汇聚 DDD 四层 + Auth 能力
│
├── ===== 微服务模式 =====
├── pandora-gateway/                      # API 网关（Spring Cloud Gateway Server WebFlux）
└── pandora-service/                      # 微服务启动父 POM
    └── pandora-service-user/             # 用户限界上下文微服务
```

## 各模块职责

### pandora-common（公共模块）

- 统一响应 `Result<T>`、分页 `PageResult<T>`、分页查询基类 `PageQuery`
- 基础异常 `BusinessException` / `SystemException`
- 错误码枚举 `ResultCode`
- 基础实体 `BaseEntity`（id、createTime、updateTime 等）
- 登录用户上下文 `LoginUser` / `LoginUserHolder`、`UserType`、`DataScopeType`、`TokenConstants`
- 注解体系：`@RequiresPermission` / `@RequiresUserType` / `@DataPermission`
- 工具类：`JsonUtils`、`DateUtils`、`SecurityUtils`、`ServletUtils`、`IdGenerator`、`BeanCopyUtils`、`AssertUtils`

### pandora-common-cloud（微服务公共模块）

- Nacos 服务注册发现与配置中心（`spring-cloud-starter-alibaba-nacos-discovery` / `-nacos-config`）
- Sentinel 限流熔断（`spring-cloud-starter-alibaba-sentinel` + `sentinel-datasource-nacos`）
- Spring Boot Web（微服务通用 REST 栈）
- 统一 `BlockException` 处理为 429 JSON 响应

> **服务间调用**：项目基于 Spring Cloud Alibaba 2025.1.0.0，**OpenFeign 作为一等公民支持**（`spring-cloud-starter-openfeign` + `spring-cloud-starter-loadbalancer` + `spring-cloud-starter-alibaba-sentinel` 熔断降级）。开发团队也可按需选择 `RestClient` + Nacos `NamingService` 或 Dubbo，三者共存。

### pandora-domain（领域层 —— DDD 核心）

- 聚合根基类 `AggregateRoot`、实体基类 `Entity`、值对象标记接口 `ValueObject`
- 领域事件基类 `DomainEvent` + 发布器接口 `DomainEventPublisher`
- 仓储接口 `Repository<T, ID>`
- 示例限界上下文：`User` 聚合根、`UserId`/`Email` 值对象、`UserRepository`、`UserCreatedEvent`
- 约束：不依赖任何框架，仅依赖 `pandora-common` 与 JDK

### pandora-application（应用层）

- 应用服务（编排领域逻辑）
- Command / Query 对象（CQRS 简化）
- DTO + Assembler（MapStruct）
- 基础设施抽象接口（FileService、AiService 等）
- 示例：`UserApplicationService`、`CreateUserCommand`、`UserDTO`

### pandora-infrastructure（基础设施层）

- **MyBatis-Flex**：`BaseMapper` + DO + 仓储实现；`InsertListener` / `UpdateListener` 替代 MP 的自动填充
- **dynamic-datasource-spring-boot4-starter**：多数据源切换（`@DS`）
- Redisson：分布式锁、限流令牌桶、布隆过滤器、缓存
- Kafka：`KafkaConfig` 幂等生产者、`KafkaProducerService` 封装
- MongoDB：文档型数据持久化
- MinIO：对象存储（实现应用层 `FileService`）
- Spring AI（OpenAI 兼容）：实现应用层 `AiService`，未配置密钥时走 Fallback
- Spring Security OAuth2 Resource Server：JWT 验签 + 多 `SecurityFilterChain`
- Spring 领域事件发布实现 `SpringDomainEventPublisher`

### pandora-api（接口层）

- REST 控制器（仅编排应用服务，无业务逻辑）
- 全局异常处理器 `GlobalExceptionHandler`
- SpringDoc / OpenAPI 3.1 配置（`springdoc-openapi-starter-webmvc-ui` 2.7.0）
- 示例：`UserController`、`FileController`、`AiController`、`SysRoleController` 等

### pandora-auth（认证授权服务器）

- 基于 **Spring Authorization Server 7.0.4** + Spring Security 7
- 多渠道认证（`AuthProvider` 策略）：用户名密码 / 短信验证码 / 微信小程序 / 微信扫码 / 微信 APP / 公众号 / 企业微信 / QQ
- JWT 令牌签发（HMAC，使用 JJWT 0.12.6）；RefreshToken 存 Redis 白名单；登出 jti 加入黑名单；代次 `tgen` 支持强制踢人
- 独立 `AuthServerApplication` 入口，端口 **9100**；运维端口 **19100**
- 社交登录通过 JustAuth 1.16.7
- Nacos 注册可选（`NACOS_ENABLED=true`）

### pandora-start（单体启动模块）

- 主启动类 `cn.pandora.PandoraApplication`
- 汇聚 `pandora-api` + `pandora-infrastructure`（传递 `pandora-application` / `pandora-domain` / `pandora-common`）
- 单体业务端口 **8080**；运维端口 **18080**；不依赖 Nacos
- 配置 profile：`dev`（默认） / `test` / `prod`（由 Maven profile 过滤 `@spring.profiles.active@`）

### pandora-gateway（API 网关）

- 基于 **Spring Cloud Gateway Server WebFlux**（新 artifact：`spring-cloud-starter-gateway-server-webflux`）
- 响应式 Redis（`spring-boot-starter-data-redis-reactive` + Redisson）
- 属性前缀 `spring.cloud.gateway.server.webflux.*`
- JWT 过滤器 `AuthGlobalFilter`：校验 Bearer Token、黑名单校验、白名单放行
- 集成 Nacos 服务发现与配置中心、Sentinel 规则拉取（`sentinel-datasource-nacos`）
- 业务端口 **8888**；运维端口 **18888**

### pandora-service / pandora-service-user（用户微服务）

- 用户限界上下文的微服务部署单元
- 启动类 `cn.pandora.service.user.UserServiceApplication`
- 依赖 `pandora-api` + `pandora-infrastructure` + `pandora-common-cloud`
- 业务端口 **8081**；运维端口 **18082**
- Nacos 服务名 `pandora-service-user`

## 双部署模式架构图

### 单体模式

```
┌────────────────────────────────────────────────┐
│        pandora-start :8080 (mgmt :18080)       │
│  ┌─────────┐  ┌────────────┐  ┌────────────┐  │
│  │pandora- │→│ pandora-   │→│ pandora-   │  │
│  │  api    │  │application │  │  domain    │  │
│  └────┬────┘  └────────────┘  └────────────┘  │
│       ↓                                        │
│  ┌────────────────────────┐                    │
│  │ pandora-infrastructure │→ MySQL9/Redis/...  │
│  └────────────────────────┘                    │
└────────────────────────────────────────────────┘
           ↑ JWT (HMAC, jti + tgen)
┌──────────────────────────────┐
│ pandora-auth :9100 (:19100)  │
│ Spring Authorization Server  │
└──────────────────────────────┘
```

### 微服务模式

```
                       ┌──────────────────────────────┐
                       │ pandora-gateway :8888 (:18888)│
                       │ Spring Cloud Gateway WebFlux  │
                       └──────┬───────────────────────┘
                              │ lb:// 路由（Nacos）
           ┌──────────────────┼──────────────────────┐
           ↓                  ↓                      ↓
┌─────────────────────┐ ┌──────────────────┐ ┌──────────────────┐
│ pandora-service-user│ │ pandora-service- │ │ pandora-auth     │
│ :8081 (:18082)      │ │ xxx :808x        │ │ :9100 (:19100)   │
│ 用户限界上下文微服务 │ │ 其他限界上下文    │ │ 服务名：         │
│ 服务名：            │ │                  │ │ pandora-auth-    │
│ pandora-service-user│ │                  │ │ server           │
└─────────┬───────────┘ └────────┬─────────┘ └──────────────────┘
          │                      │
          └──────────┬───────────┘
                     ↓
     ┌──────────────────────────────────────────────────┐
     │ Nacos 集群 :8140,8550,8960 (192.168.50.168)      │
     │ 服务注册 + 配置中心 + Sentinel 规则数据源         │
     └──────────────────────────────────────────────────┘
```

> Nacos Group 约定以 `spring.profiles.active` 命名（`dev` / `test` / `prod`），非 `DEFAULT_GROUP`。
> Nacos 远程 DataId 约定：`{spring.application.name}-{profile}.yml`，示例 `pandora-gateway-dev.yml`。

## 模块依赖关系

```
单体模式:
pandora-start ──→ pandora-infrastructure ──→ pandora-application ──→ pandora-domain ──→ pandora-common
    │                                             │
    └──→ pandora-api ────────────────────────────┘

微服务模式:
pandora-service-user ──→ pandora-common-cloud ──→ pandora-common
       │
       ├──→ pandora-infrastructure ──→ pandora-application ──→ pandora-domain ──→ pandora-common
       │
       └──→ pandora-api

pandora-gateway   ──→ pandora-common（独立，无 DDD 分层依赖；仅此模块和 pandora-common-cloud 允许直接用 spring-cloud-*）
pandora-auth      ──→ pandora-common（独立部署；可选注册 Nacos）
```

**关键约束：**
- 领域层（`pandora-domain`）不依赖任何基础设施和框架，保持纯净
- DDD 四层在单体和微服务两种模式下完全复用，零修改
- 除 `pandora-gateway` 外，任何模块禁止直接依赖 `org.springframework.cloud:spring-cloud-starter-*`

## 服务 / 端口规划

| 服务（Nacos 服务名） | 模块 | 业务端口 | 运维端口 | 说明 |
|------|------|---------|---------|------|
| `pandora-arch` | pandora-start | 8080 | 18080 | 单体模式主应用（不注册 Nacos） |
| `pandora-auth-server` | pandora-auth | 9100 | 19100 | OAuth2 授权服务器 |
| `pandora-gateway` | pandora-gateway | 8888 | 18888 | API 网关（仅微服务模式） |
| `pandora-service-user` | pandora-service-user | 8081 | 18082 | 用户微服务 |

## 基础设施默认地址

| 组件 | 默认地址 | 说明 |
|------|---------|------|
| MySQL | localhost:3306 / root / 123456 | 数据库 `ddd_platform` |
| Redis | localhost:6379 | 所有服务使用同一 database（默认 `1`），Redisson 客户端 |
| MinIO | 127.0.0.1:9000（API）/ :9001（Console） | 桶名 `corp-web` |
| MongoDB | localhost:27017 | 按需启用（`pandora.mongodb.enabled`） |
| Kafka | localhost:9092 | 按需启用（`pandora.kafka.enabled`） |
| Nacos | `192.168.50.168:8140,8550,8960`（3 节点集群） | `NACOS_SERVER_ADDR` 可覆盖 |
| Sentinel Dashboard | localhost:8858（可选） | 流控控制台 |

## 环境 Profile

根 POM 定义 `dev`（默认） / `test` / `prod` 三个 Profile，仅过滤 `application.yml` 中的 `@spring.profiles.active@` 占位符：

```bash
mvn clean package -P dev   -DskipTests
mvn clean package -P test  -DskipTests
mvn clean package -P prod  -DskipTests
```

---

> **作者**：vahoa  
> **日期**：2026 年  
> **作品**：pandora-arch · DDD 架构底座  
> **版权**：© 2026 vahoa. All rights reserved.
