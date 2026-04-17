# OAuth2 认证授权指南

> 基线：JDK 25 + Spring Boot 4.0.5 + Spring Security 7.x + Spring Authorization Server 7.0.4

`pandora-auth-server`（业务端口 `9100` / 运维端口 `19100`）作为系统统一的认证与授权中心，同时具备两种能力：

1. **OAuth2 / OIDC 授权服务器**：基于 `spring-boot-starter-oauth2-authorization-server` 与 Spring Authorization Server 7.0.4，对外提供标准 OAuth2 端点。
2. **业务化登录编排层**：基于 `AuthProvider` 策略，对接项目的账密、短信、邮箱、第三方（JustAuth）、扫码登录等场景，下发符合项目规范的 JWT Token。

资源服务器（`pandora-start`、`pandora-service-*`）通过 HS256 HMAC Secret 或 `issuer-uri` 校验 JWT。

---

## 1. 架构与端点总览

```
客户端                    授权服务器 pandora-auth-server(:9100)        资源服务器 (:8080 / :8081 ... 网关 :8888)
  │                           │                                            │
  │  业务登录：/auth/**        │                                            │
  │──────────────────────────▶│  AuthProvider 策略校验身份                 │
  │  ← JWT Access / Refresh ──│  JwtUtils 签发                             │
  │                                                                        │
  │  标准 OAuth2：/oauth2/**   │                                            │
  │──────────────────────────▶│  SAS 颁发 Access Token（JWT）              │
  │                                                                        │
  │  GET /api/users/1                                                      │
  │  Authorization: Bearer <token>                                         │
  │──────────────────────────────────────────────────────────────────────▶│
  │                                                                        │ 使用 HMAC Secret / JWKs 验签
  │  ← 业务数据 ─────────────────────────────────────────────────────────│
```

### 主要端点

| 端点 | 方法 | 场景 | 说明 |
|------|------|------|------|
| `/auth/admin/login` | POST | B 端后台登录 | 账号密码，返回 `accessToken` / `refreshToken` |
| `/auth/app/login` | POST | C 端 App 登录 | 支持 `SMS_CODE` / `USERNAME_PASSWORD` 等 |
| `/auth/sms/send` | POST | 短信验证码 | 发送手机验证码 |
| `/auth/email/send` | POST | 邮箱验证码 | 发送邮箱验证码 |
| `/auth/social/{platform}/callback` | GET | 第三方登录回调 | 依赖 JustAuth |
| `/auth/qr/**` | - | 扫码登录 | `ticket / scan / confirm / poll` |
| `/auth/refresh` | POST | 刷新令牌 | 基于 `refreshToken` |
| `/oauth2/token` | POST | 标准 OAuth2 | SAS 颁发 JWT（client_credentials / authorization_code 等） |
| `/oauth2/jwks` | GET | 公钥端点 | 资源服务器可通过 `issuer-uri` 自动拉取 |
| `/.well-known/openid-configuration` | GET | OIDC 发现 | 元数据 |

---

## 2. Token 与配置规范

### 2.1 项目 JWT（`auth.jwt`）

`pandora-auth-server` 业务登录接口使用 HMAC（HS256）算法签发项目内部使用的 JWT：

```yaml
auth:
  jwt:
    secret: ${AUTH_JWT_SECRET:0123456789abcdef0123456789abcdef}
    access-token-ttl-minutes: 30
    refresh-token-ttl-days: 7
    issuer: pandora-auth-server
```

> Secret 必须至少 256 bit（32 字节），生产环境通过 `AUTH_JWT_SECRET` 环境变量注入，不允许提交到仓库。

资源服务器统一使用同一个 Secret 校验 JWT：

```yaml
auth:
  jwt:
    secret: ${AUTH_JWT_SECRET:0123456789abcdef0123456789abcdef}
```

### 2.2 标准 OAuth2 模式（SAS）

如果采用标准 OAuth2 流程（如 B2B SaaS、对接外部 OIDC 客户端），使用 SAS 颁发的 RS256 Token，资源服务器改用 `issuer-uri` 接入：

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://pandora-auth-server:9100
```

`ResourceServerConfig` 会同时支持 HS256（项目 JWT）与 RS256（SAS JWT），具体见 `pandora-common` 的安全组件。

---

## 3. 业务登录示例

### 3.1 B 端账密登录

```bash
curl -X POST http://localhost:9100/auth/admin/login \
  -H "Content-Type: application/json" \
  -d '{
    "loginType": "USERNAME_PASSWORD",
    "username": "admin",
    "password": "admin123"
  }'
```

响应：

```json
{
  "code": 200,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi...",
    "expiresIn": 1800,
    "tokenType": "Bearer"
  }
}
```

### 3.2 C 端短信登录

```bash
# 1. 发送短信验证码
curl -X POST http://localhost:9100/auth/sms/send \
  -H "Content-Type: application/json" \
  -d '{"mobile": "13800138000", "scene": "LOGIN"}'

# 2. 使用验证码登录
curl -X POST http://localhost:9100/auth/app/login \
  -H "Content-Type: application/json" \
  -d '{
    "loginType": "SMS_CODE",
    "mobile": "13800138000",
    "code": "123456"
  }'
```

### 3.3 刷新令牌

```bash
curl -X POST http://localhost:9100/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "<refresh_token>"}'
```

### 3.4 访问资源服务器

单体模式：

```bash
curl -H "Authorization: Bearer <access_token>" \
  http://localhost:8080/api/users/1
```

微服务模式（经网关）：

```bash
curl -H "Authorization: Bearer <access_token>" \
  http://localhost:8888/api/users/1
```

---

## 4. 标准 OAuth2 示例

> 适用于与三方客户端对接、或明确希望使用 RS256 + JWKs 场景。客户端、Scope 均由 `RegisteredClientRepository` 管理，默认使用 JDBC 持久化。

### 4.1 Client Credentials

```bash
curl -X POST http://localhost:9100/oauth2/token \
  -u "pandora-service-client:pandora-service-secret" \
  -d "grant_type=client_credentials&scope=read write"
```

### 4.2 Authorization Code + PKCE

```
GET http://localhost:9100/oauth2/authorize
  ?response_type=code
  &client_id=pandora-web-client
  &redirect_uri=http://localhost:8080/login/oauth2/code/pandora-web-client
  &scope=openid profile read write
  &code_challenge=<S256-challenge>
  &code_challenge_method=S256
```

拿到 `code` 后换取 Token：

```bash
curl -X POST http://localhost:9100/oauth2/token \
  -u "pandora-web-client:pandora-web-secret" \
  -d "grant_type=authorization_code&code=<code>&redirect_uri=http://localhost:8080/login/oauth2/code/pandora-web-client&code_verifier=<verifier>"
```

---

## 5. 权限与注解

资源服务器使用 Spring Security 7 的方法级注解，与 JWT 中携带的权限/角色匹配：

```java
@PreAuthorize("hasAuthority('user:update')")
@PutMapping("/{id}/disable")
public Result<Void> disable(@PathVariable Long id) { ... }

@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable Long id) { ... }
```

- 细粒度权限：`@PreAuthorize("hasAuthority('xxx')")`
- 角色：`@PreAuthorize("hasRole('ROLE_XXX')")`（`hasRole` 会自动拼接 `ROLE_` 前缀）
- 项目还提供 `@RequiresPermission`、`@RequiresRole` 自定义注解，由 `pandora-common` 统一封装，语义更直观。

---

## 6. 扩展点

| 场景 | 扩展方式 |
|------|---------|
| 新增登录方式 | 实现 `AuthProvider` 接口，注册为 Spring Bean；在 `LoginService` 中会根据 `loginType` 路由 |
| 自定义 JWT Claims | 修改 `JwtUtils`，在 `generateAccessToken` 前向 `claims` 写入业务字段（如租户、组织 ID） |
| 数据库用户 | `LoginUserQueryService` / `UserDomainService` 已对接 `pandora-service-user`，通过 Nacos 注册的用户服务查询；无需再使用 `InMemoryUserDetailsManager` |
| 数据库客户端 | 默认使用 `JdbcRegisteredClientRepository`，SQL 见 `docs/sql/schema-auth.sql` |
| 第三方登录 | 基于 JustAuth 1.16.7，扩展 `SocialAuthProvider` 并在 `application.yml` 配置对应平台 clientId/secret |

---

## 7. 常见问题

- **401 Unauthorized**：确认资源服务器 `auth.jwt.secret` 与授权服务器一致（或 `issuer-uri` 可达）。
- **403 Forbidden**：Token 校验通过但缺少权限，检查 JWT 中的 `authorities` / `scope` 与注解要求是否匹配。
- **时钟偏移导致 `exp` 校验失败**：生产环境请开启 NTP，或在资源服务器调整 clock skew。
- **Token 在网关被拦截**：默认白名单仅包含 `/auth/**`、`/oauth2/**`、`/.well-known/**`、`/actuator/**`，其它路径必须带 `Authorization` 头。

---

> **作者**：vahoa  
> **日期**：2026 年  
> **作品**：pandora-arch · DDD 架构底座  
> **版权**：© 2026 vahoa. All rights reserved.
