# Spring Cloud Alibaba 微服务指南

> 基线：JDK 25 + Spring Boot 4.0.5 + Spring Cloud 2025.1.0（Oakwood，仅 `pandora-gateway`）+ Spring Cloud Alibaba 2025.1.0.0

## 1. 组件概览

| 组件 | 版本 | 用途 | 对应模块 |
|------|------|------|---------|
| Nacos | Server 2.x，客户端由 SCA 2025.1.0.0 引入 | 服务注册发现 + 配置中心 | `pandora-common-cloud` |
| Sentinel | 1.8.8 | 流量控制与熔断降级 | `pandora-common-cloud` |
| Spring Cloud Gateway Server WebFlux | 2025.1.0 | API 网关（响应式） | `pandora-gateway` |
| Spring Cloud LoadBalancer | 2025.1.0 | 客户端负载均衡 | `pandora-gateway`、OpenFeign 调用方等 |
| Spring Cloud OpenFeign | 2025.1.0 | 声明式 HTTP 调用 | 任意业务微服务（首选） |
| RestClient / Nacos NamingService | Spring Boot 4.0.5 内置 | 服务间同步调用（低依赖场景） | 各业务微服务 |
| Dubbo | 3.x（可选） | 高性能 RPC | 需要 RPC 协议的场景 |

> **服务间调用说明**：项目基于 Spring Cloud Alibaba，**OpenFeign 为推荐方式**，与 LoadBalancer、Sentinel 无缝集成。如果某模块希望减少依赖或追求更底层控制，可选用 `RestClient + Nacos NamingService`；需要 RPC 协议时可接入 Dubbo。三者可在不同微服务里并存。

### 服务与端口

| 服务 | 业务端口 | 运维端口 | Nacos 服务名 |
|------|---------|---------|-------------|
| `pandora-auth-server` | 9100 | 19100 | `pandora-auth-server` |
| `pandora-service-user` | 8081 | 18082 | `pandora-service-user` |
| `pandora-gateway` | 8888 | 18888 | `pandora-gateway` |

---

## 2. Nacos 服务注册与配置中心

### 2.1 安装启动

```bash
# 下载 Nacos 2.x
# https://github.com/alibaba/nacos/releases

# 单机模式启动（本地开发）
sh bin/startup.sh -m standalone
startup.cmd -m standalone  # Windows
```

生产环境建议 3 节点集群部署。项目默认指向：

```
NACOS_SERVER_ADDR=192.168.50.168:8140,192.168.50.168:8550,192.168.50.168:8960
```

控制台：`http://<nacos-host>:<port>/nacos`（默认账号 `nacos/nacos`，生产必须修改）。

### 2.2 服务注册

各微服务在 `application.yml` 中配置（由 `pandora-common-cloud` 统一提供默认值）：

```yaml
spring:
  application:
    name: pandora-service-user
  profiles:
    active: dev
  cloud:
    nacos:
      discovery:
        enabled: ${NACOS_ENABLED:true}
        server-addr: ${NACOS_SERVER_ADDR:192.168.50.168:8140,192.168.50.168:8550,192.168.50.168:8960}
        namespace: ${NACOS_NAMESPACE:public}
        group: ${spring.profiles.active}   # 按环境分组：dev / test / prod
```

> **Group 命名约定**：严禁使用 `DEFAULT_GROUP`，统一使用 `spring.profiles.active`（`dev` / `test` / `prod`）。

### 2.3 配置中心

Spring Boot 4 推荐使用 `spring.config.import` 方式：

```yaml
spring:
  config:
    import:
      - optional:nacos:${spring.application.name}.yml?group=${spring.profiles.active}
      - optional:nacos:${spring.application.name}-${spring.profiles.active}.yml?group=${spring.profiles.active}
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR}
        namespace: ${NACOS_NAMESPACE:public}
        file-extension: yml
```

Data ID 命名规范：

| Data ID | Group | 用途 |
|---------|-------|------|
| `pandora-service-user.yml` | `dev` | 公共配置 |
| `pandora-service-user-dev.yml` | `dev` | Profile 专属（可选） |
| `pandora-common.yml` | `dev` | 所有微服务共享的通用配置（可用 `shared-configs` 引入） |

---

## 3. Sentinel 流量控制

### 3.1 安装 Dashboard

```bash
# 使用 Sentinel 1.8.8
java -jar sentinel-dashboard-1.8.8.jar --server.port=8858
```

控制台：`http://localhost:8858`（默认账号 `sentinel/sentinel`，生产必须修改）。

### 3.2 微服务配置

`pandora-common-cloud` 已自动集成 Sentinel，在 `application.yml` 中指定 Dashboard 地址：

```yaml
spring:
  cloud:
    sentinel:
      enabled: ${SENTINEL_ENABLED:true}
      transport:
        dashboard: ${SENTINEL_DASHBOARD:localhost:8858}
        port: 8719
      eager: true        # 启动即主动向 Dashboard 注册
```

### 3.3 统一限流响应

在 Spring Cloud Alibaba 2025.x 中，Sentinel 对 WebFlux 的拓展 API 已调整为实现 `BlockRequestHandler`（`SentinelBlockHandlerAdapter` 会委托该 Bean）。项目在 `pandora-common-cloud` 的 `SentinelConfig` 中注册了统一响应：

```json
{
  "code": 429,
  "message": "请求过于频繁，请稍后重试",
  "data": null,
  "timestamp": 1747000000000
}
```

> 由 Web MVC（资源服务器业务端）与 WebFlux（网关）分别注册对应的 BlockHandler Bean，保证两种协议栈下的限流响应一致。

---

## 4. 服务间调用

项目支持三种服务间调用方式，按"上层易用性 → 底层灵活性"排序为：OpenFeign（推荐）→ RestClient + Nacos NamingService → Dubbo。

### 4.1 OpenFeign（推荐，Spring Cloud 原生方式）

#### 依赖

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
<!-- 可选：Sentinel 为 Feign 做熔断降级 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
</dependency>
```

#### 启用

```java
@SpringBootApplication
@EnableFeignClients(basePackages = "cn.pandora.**.client")
public class OrderServiceApplication { }
```

```yaml
feign:
  sentinel:
    enabled: true        # 打开 Sentinel 熔断
  client:
    config:
      default:
        connect-timeout: 3000
        read-timeout: 5000
```

#### 声明客户端

```java
@FeignClient(name = "pandora-service-user", path = "/api/users",
             fallback = UserClientFallback.class)
public interface UserClient {

    @GetMapping("/{id}")
    Result<UserDTO> getById(@PathVariable("id") Long id);
}

@Component
class UserClientFallback implements UserClient {
    @Override
    public Result<UserDTO> getById(Long id) {
        return Result.fail("user-service 暂不可用");
    }
}
```

#### JWT 透传

通过 `RequestInterceptor` 将当前请求的 `Authorization` 头自动透传给被调方，`pandora-common-cloud` 已封装默认实现：

```java
@Bean
public RequestInterceptor authRelayInterceptor() {
    return template -> {
        String token = CurrentRequestHolder.getAuthorization();
        if (StringUtils.hasText(token)) {
            template.header(HttpHeaders.AUTHORIZATION, token);
        }
    };
}
```

### 4.2 基于 Nacos NamingService + RestClient 的写法（可选）

适用于希望零 Spring Cloud 耦合、或完全手动控制选址 / 重试 / 熔断的场景。

```java
@Configuration
public class UserClientConfig {

    @Bean
    public RestClient userRestClient(NamingService namingService) {
        return RestClient.builder()
            .requestInterceptor((request, body, execution) -> {
                // 1. 从 Nacos 拿到 pandora-service-user 的健康实例
                Instance instance = namingService.selectOneHealthyInstance("pandora-service-user");
                URI origin = request.getURI();
                URI target = UriComponentsBuilder.newInstance()
                    .scheme("http")
                    .host(instance.getIp()).port(instance.getPort())
                    .path(origin.getPath()).query(origin.getQuery())
                    .build().toUri();
                request.getHeaders().setOrRemove("Host", target.getHost());
                // 2. 透传 Authorization
                Optional.ofNullable(ServletHolder.getAuthorization())
                    .ifPresent(token -> request.getHeaders().setBearerAuth(token));
                return execution.execute(new UriChangedHttpRequest(request, target), body);
            })
            .build();
    }
}
```

使用方式：

```java
@Service
public class OrderApplicationService {

    private final RestClient userRestClient;

    public void createOrder(CreateOrderCommand cmd) {
        Result<UserDTO> user = userRestClient.get()
            .uri("/api/users/{id}", cmd.getUserId())
            .retrieve()
            .body(new ParameterizedTypeReference<Result<UserDTO>>() {});
        // ...
    }
}
```

> JWT 透传：依赖 `RequestContextHolder` / 自定义 `SecurityContextHolder`，在拦截器中取出当前请求的 `Authorization` 头转发给下游。

### 4.3 Dubbo（可选）

如果需要高性能 RPC，可引入 `dubbo-spring-boot-starter`（通过 Nacos 做注册中心），在服务侧 `@DubboService`，消费侧 `@DubboReference`。

---

## 5. Spring Cloud Gateway Server WebFlux

Spring Cloud 2025.x 已将原 `spring-cloud-starter-gateway` 拆分：

| 旧 artifactId | 新 artifactId | 说明 |
|--------------|---------------|------|
| `spring-cloud-starter-gateway` | `spring-cloud-starter-gateway-server-webflux` | 响应式网关（本项目使用） |
| — | `spring-cloud-starter-gateway-server-webmvc` | Servlet 版（本项目不使用） |

属性前缀同步变更为 `spring.cloud.gateway.server.webflux.*`。

### 5.1 路由配置

在 `pandora-gateway/src/main/resources/application.yml`：

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          discovery:
            locator:
              enabled: false
          routes:
            - id: pandora-auth-server
              uri: lb://pandora-auth-server
              predicates:
                - Path=/auth/**,/oauth2/**,/.well-known/**
            - id: pandora-service-user
              uri: lb://pandora-service-user
              predicates:
                - Path=/api/users/**
```

- `lb://` 走 Spring Cloud LoadBalancer + Nacos 服务发现。
- 严禁使用过期的 `spring.cloud.gateway.routes` 前缀，启动时会报配置校验失败。

### 5.2 全局认证过滤器

`AuthGlobalFilter` 校验 `Authorization: Bearer <token>`，白名单直接放行：

- `/auth/**`
- `/oauth2/**`
- `/.well-known/**`
- `/actuator/**`

### 5.3 新增服务路由

```yaml
routes:
  - id: pandora-service-order
    uri: lb://pandora-service-order
    predicates:
      - Path=/api/orders/**
    filters:
      - StripPrefix=0
```

---

## 6. 两种部署模式对比

| 特性 | 单体模式 (`pandora-start`) | 微服务模式 (`pandora-service-*`) |
|------|----------------------|---------------------------|
| 对外入口端口 | 8080（业务）/ 18080（运维） | 8888（网关）/ 18888（运维） |
| 依赖 Nacos | 否（`NACOS_ENABLED=false`） | 是 |
| 服务间通信 | 方法调用 | OpenFeign（推荐） / RestClient + NamingService / Dubbo |
| 流量控制 | 无 / JVM 级别 | Sentinel Dashboard |
| 配置管理 | 本地 yml | Nacos 配置中心 + 本地 yml 组合 |
| 水平扩展 | 整体扩容 | 按服务独立扩容 |
| 部署复杂度 | 低 | 较高（需 Nacos、Sentinel 等基础设施） |
| 适用场景 | 早期 / 中小规模 | 中大规模 / 多团队协作 |

---

## 7. 生产环境建议

1. **Nacos**：至少 3 节点集群部署，数据库使用独立 MySQL；启用 鉴权 (`nacos.core.auth.enabled=true`) 并修改默认账号。
2. **Sentinel**：Dashboard 需要高可用方案或持久化规则（如 Nacos / Apollo 规则源）。
3. **网关**：开启访问日志与限流黑名单；前置 Nginx/SLB 做 TLS 卸载与 IP 过滤。
4. **配置安全**：敏感配置（DB 密码、JWT Secret、第三方密钥）统一通过环境变量或 Nacos 加密字段注入，严禁写入代码仓库。
5. **可观测性**：统一接入 OpenTelemetry / Prometheus / Loki，日志包含 `traceId`、`spanId`。

---

> **作者**：vahoa  
> **日期**：2026 年  
> **作品**：pandora-arch · DDD 架构底座  
> **版权**：© 2026 vahoa. All rights reserved.
