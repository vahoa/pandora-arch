package cn.pandora.gateway.constant;

/**
 * 网关模块专用常量。
 * <p>
 * 所有跨下游微服务的透传 Header 统一以 {@code X-Pandora-} 前缀，避免与业务 Header 冲突。
 * 各下游服务可通过 {@code cn.pandora.common.security.LoginUserHolder} 的上游过滤器读取。
 */
public final class GatewayConstants {

    private GatewayConstants() {}

    /* -------------------- 透传用户上下文 Header（网关 -> 下游） -------------------- */

    public static final String HEADER_USER_ID       = "X-Pandora-User-Id";
    public static final String HEADER_USERNAME      = "X-Pandora-Username";
    public static final String HEADER_USER_TYPE     = "X-Pandora-User-Type";
    public static final String HEADER_TENANT_ID     = "X-Pandora-Tenant-Id";
    public static final String HEADER_ROLES         = "X-Pandora-Roles";
    public static final String HEADER_TRACE_ID      = "X-Pandora-Trace-Id";
    public static final String HEADER_REQUEST_ID    = "X-Pandora-Request-Id";
    public static final String HEADER_CLIENT_IP     = "X-Pandora-Client-Ip";

    /* -------------------- 限流 Redis Key 前缀 -------------------- */

    /** 基于用户维度限流：ratelimit:user:{userId} */
    public static final String RATE_LIMIT_USER_PREFIX = "ratelimit:user:";
    /** 基于 IP 维度限流：ratelimit:ip:{ip} */
    public static final String RATE_LIMIT_IP_PREFIX   = "ratelimit:ip:";
    /** 基于 API 维度限流：ratelimit:api:{path} */
    public static final String RATE_LIMIT_API_PREFIX  = "ratelimit:api:";

    /* -------------------- JWT 相关 -------------------- */

    public static final String JWT_HEADER  = "Authorization";
    public static final String JWT_PREFIX  = "Bearer ";
    public static final String CLAIM_USER_ID   = "userId";
    public static final String CLAIM_USERNAME  = "username";
    public static final String CLAIM_USER_TYPE = "userType";
    public static final String CLAIM_TENANT_ID = "tenantId";
    public static final String CLAIM_ROLES     = "roles";
}
