# Spring Cloud Alibaba 微服务指南

## 组件概览

| 组件 | 用途 | 对应模块 |
|------|------|---------|
| Nacos | 服务注册发现 + 配置中心 | pandora-common-cloud |
| Sentinel | 流量控制与熔断降级 | pandora-common-cloud |
| OpenFeign | 声明式服务调用 | pandora-common-cloud |
| Spring Cloud Gateway | API 网关 | pandora-gateway |
| LoadBalancer | 客户端负载均衡 | pandora-common-cloud / pandora-gateway |

## Nacos 服务注册与配置中心

### 安装启动

```bash
# 下载 Nacos 2.x
# https://github.com/alibaba/nacos/releases

# 单机模式启动
sh bin/startup.sh -m standalone

# Windows
startup.cmd -m standalone
```

控制台：http://localhost:8848/nacos（nacos/nacos）

### 服务注册

各微服务在 `application.yml` 中配置：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: public
```

启动后在 Nacos 控制台「服务管理 → 服务列表」可以看到注册的服务。

### 配置中心

支持从 Nacos 拉取远程配置：

```yaml
spring:
  config:
    import:
      - optional:nacos:${spring.application.name}.yml
```

在 Nacos 控制台「配置管理 → 配置列表」中创建 Data ID 为 `pandora-service-user.yml` 的配置项，应用会自动拉取并合并。

## Sentinel 流量控制

### 安装 Dashboard

```bash
# 下载 Sentinel Dashboard
java -jar sentinel-dashboard-1.8.7.jar --server.port=8858
```

控制台：http://localhost:8858

### 配置

各微服务中已通过 `pandora-common-cloud` 自动集成 Sentinel，在 `application.yml` 中指定 Dashboard 地址：

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: localhost:8858
        port: 8719
```

### 统一限流响应

当触发限流时，返回统一的 JSON 格式：

```json
{
  "code": 429,
  "message": "请求过于频繁，请稍后重试",
  "data": null,
  "timestamp": 1709712000000
}
```

此行为在 `pandora-common-cloud` 的 `SentinelConfig` 中定义。

## OpenFeign 服务间调用

### 定义 Feign 客户端

在需要调用其他服务的模块中定义接口：

```java
@FeignClient(name = "pandora-service-user", path = "/api/users")
public interface UserFeignClient {

    @GetMapping("/{id}")
    Result<UserDTO> getUser(@PathVariable("id") Long id);
}
```

### JWT 令牌自动传递

`pandora-common-cloud` 中的 `FeignConfig` 已配置全局请求拦截器，会自动将当前请求的 `Authorization` 头传递到 Feign 调用中，无需手动处理。

### 使用 Feign 客户端

```java
@Service
public class OrderApplicationService {

    private final UserFeignClient userFeignClient;

    public OrderApplicationService(UserFeignClient userFeignClient) {
        this.userFeignClient = userFeignClient;
    }

    public void createOrder(CreateOrderCommand command) {
        Result<UserDTO> userResult = userFeignClient.getUser(command.getUserId());
        // ...
    }
}
```

## Spring Cloud Gateway 网关

### 路由配置

在 `pandora-gateway/src/main/resources/application.yml` 中配置路由规则：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: pandora-service-user
          uri: lb://pandora-service-user
          predicates:
            - Path=/api/users/**

        - id: pandora-auth-server
          uri: lb://pandora-auth-server
          predicates:
            - Path=/auth/**,/oauth2/**,/.well-known/**
```

- `lb://` 前缀表示使用 LoadBalancer 进行服务发现和负载均衡
- `predicates` 定义路径匹配规则

### 全局认证过滤器

`AuthGlobalFilter` 拦截所有请求，校验 `Authorization: Bearer <token>` 是否存在。白名单路径直接放行：

- `/auth/` - 认证相关
- `/oauth2/` - OAuth2 端点
- `/.well-known/` - OIDC 发现端点
- `/actuator/` - 健康检查

### 添加新服务路由

新增微服务后，在网关的 `application.yml` 中添加路由：

```yaml
routes:
  - id: pandora-service-order
    uri: lb://pandora-service-order
    predicates:
      - Path=/api/orders/**
```

## 两种模式对比

| 特性 | 单体模式 (`pandora-start`) | 微服务模式 (`pandora-service-*`) |
|------|----------------------|---------------------------|
| 入口端口 | 8080 | 9999（网关） |
| 依赖 Nacos | 否 | 是 |
| 服务间通信 | 方法调用 | OpenFeign + LoadBalancer |
| 流量控制 | 无 | Sentinel |
| 配置管理 | 本地 yml | Nacos 配置中心 |
| 水平扩展 | 整体扩容 | 按服务独立扩容 |
| 部署复杂度 | 低 | 高（需要 Nacos 等基础设施） |
| 适用场景 | 早期/小规模 | 中大规模/团队协作 |
