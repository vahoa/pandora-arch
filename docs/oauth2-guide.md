# OAuth2 认证授权指南

## 认证流程

```
客户端                    授权服务器(:9000)              资源服务器(:8080/8081)
  │                           │                              │
  │  POST /oauth2/token       │                              │
  │  (client_credentials)     │                              │
  │──────────────────────────→│                              │
  │                           │ 验证客户端凭证                │
  │  ← JWT Access Token ─────│                              │
  │                           │                              │
  │  GET /api/users/1                                        │
  │  Authorization: Bearer <token>                           │
  │─────────────────────────────────────────────────────────→│
  │                                                          │ 验证 JWT 签名
  │  ← 返回用户数据 ────────────────────────────────────────│
```

## 预置客户端

| 客户端ID | 密钥 | 认证方式 | 授权类型 | 用途 |
|----------|------|---------|---------|------|
| pandora-web-client | pandora-web-secret | CLIENT_SECRET_BASIC | authorization_code, refresh_token, client_credentials | Web 前端应用 |
| pandora-service-client | pandora-service-secret | CLIENT_SECRET_BASIC | client_credentials | 服务间调用 |

## 预置用户

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | ADMIN, USER |
| user | user123 | USER |

## 使用示例

### 客户端凭证模式（服务间调用）

```bash
curl -X POST http://localhost:9000/oauth2/token \
  -u "pandora-service-client:pandora-service-secret" \
  -d "grant_type=client_credentials&scope=read write"
```

响应：

```json
{
  "access_token": "eyJraWQiOi...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "read write"
}
```

### 授权码模式（用户登录）

1. 浏览器访问授权地址：

```
http://localhost:9000/oauth2/authorize?response_type=code&client_id=pandora-web-client&redirect_uri=http://localhost:8080/login/oauth2/code/pandora-web-client&scope=openid profile read write
```

2. 用户登录并授权后，回调地址获取 `code`。

3. 使用 `code` 换取令牌：

```bash
curl -X POST http://localhost:9000/oauth2/token \
  -u "pandora-web-client:pandora-web-secret" \
  -d "grant_type=authorization_code&code=<code>&redirect_uri=http://localhost:8080/login/oauth2/code/pandora-web-client"
```

### 访问受保护的 API

```bash
curl -H "Authorization: Bearer <access_token>" \
  http://localhost:8080/api/users/1
```

### 访问需要特定权限的 API

禁用/启用用户接口需要 `SCOPE_admin` 权限：

```bash
# 先获取包含 admin scope 的令牌
curl -X POST http://localhost:9000/oauth2/token \
  -u "pandora-service-client:pandora-service-secret" \
  -d "grant_type=client_credentials&scope=admin"

# 使用该令牌调用管理接口
curl -X PUT -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/users/1/disable
```

## 资源服务器配置说明

资源服务器通过 `spring.security.oauth2.resourceserver.jwt.issuer-uri` 指向授权服务器，自动获取 JWK 公钥验证 JWT 签名。

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9000
```

## 自定义扩展

### 替换为数据库用户存储

修改 `pandora-auth` 模块中的 `DefaultSecurityConfig.userDetailsService()` 方法，替换 `InMemoryUserDetailsManager` 为自定义的 `UserDetailsService` 实现。

### 替换为数据库客户端存储

修改 `AuthorizationServerConfig.registeredClientRepository()` 方法，替换 `InMemoryRegisteredClientRepository` 为 `JdbcRegisteredClientRepository`。
