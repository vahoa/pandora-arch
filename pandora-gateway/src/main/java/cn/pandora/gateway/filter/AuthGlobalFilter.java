package cn.pandora.gateway.filter;

import cn.pandora.common.result.Result;
import cn.pandora.common.result.ResultCode;
import cn.pandora.common.security.TokenConstants;
import cn.pandora.gateway.constant.GatewayConstants;
import cn.pandora.gateway.config.GatewayJwtProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关全局认证过滤器。
 * <p>
 * 职责：
 * <ol>
 *   <li>命中白名单 -> 直接放行；</li>
 *   <li>校验 Authorization 头存在且格式正确；</li>
 *   <li>HMAC-SHA256 本地验签 + 过期校验；</li>
 *   <li>可选：查 Redis 黑名单（{@link TokenConstants#BLACKLIST_PREFIX}）；</li>
 *   <li>把 userId / username / userType / roles / tenantId 写入下游 Header 透传给微服务；</li>
 *   <li>任何失败都以统一 {@link Result} JSON 返回，不把异常抛到 Netty。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final GatewayJwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    /**
     * Reactive Redis 模板：黑名单校验非阻塞。
     * 配置为可选（{@link Autowired#required()} = false）—— 在不带 Redis 的本地烟雾测试场景下网关仍可启动。
     */
    @Autowired(required = false)
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1) 白名单直接放行
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // 2) 必须携带 Bearer Token
        String authorization = request.getHeaders().getFirst(GatewayConstants.JWT_HEADER);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(GatewayConstants.JWT_PREFIX)) {
            return unauthorized(exchange, "缺少 Authorization 请求头或格式错误");
        }
        String token = authorization.substring(GatewayConstants.JWT_PREFIX.length()).trim();

        // 3) 本地验签 & 过期校验
        Claims claims;
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
            Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            claims = jws.getPayload();
        } catch (ExpiredJwtException e) {
            return unauthorized(exchange, "Token 已过期");
        } catch (Exception e) {
            log.warn("JWT 校验失败, path={}, err={}", path, e.getMessage());
            return unauthorized(exchange, "Token 非法");
        }

        // 4) 黑名单校验（可选依赖 Redis）
        if (jwtProperties.isBlacklistEnabled() && redisTemplate != null) {
            String jti = claims.getId();
            if (!StringUtils.hasText(jti)) {
                return unauthorized(exchange, "Token 缺少 jti 声明");
            }
            return redisTemplate.hasKey(TokenConstants.BLACKLIST_PREFIX + jti)
                    .defaultIfEmpty(false)
                    .flatMap(inBlacklist -> {
                        if (Boolean.TRUE.equals(inBlacklist)) {
                            return unauthorized(exchange, "Token 已登出");
                        }
                        return forward(exchange, chain, claims);
                    });
        }
        return forward(exchange, chain, claims);
    }

    /** 白名单匹配（Ant Style） */
    private boolean isWhiteListed(String path) {
        List<String> whiteList = jwtProperties.getWhiteList();
        if (CollectionUtils.isEmpty(whiteList)) {
            return false;
        }
        return whiteList.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    /** 将 JWT claims 透传到下游微服务的请求头中 */
    @SuppressWarnings("unchecked")
    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain, Claims claims) {
        String userId    = String.valueOf(claims.get(GatewayConstants.CLAIM_USER_ID, Object.class));
        String username  = claims.get(GatewayConstants.CLAIM_USERNAME, String.class);
        String userType  = claims.get(GatewayConstants.CLAIM_USER_TYPE, String.class);
        String tenantId  = claims.get(GatewayConstants.CLAIM_TENANT_ID, String.class);
        Object rolesObj  = claims.get(GatewayConstants.CLAIM_ROLES);
        String rolesStr  = rolesObj == null ? "" : (rolesObj instanceof List<?> list ? String.join(",", list.stream().map(String::valueOf).toList()) : rolesObj.toString());

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(GatewayConstants.HEADER_USER_ID,   safe(userId))
                .header(GatewayConstants.HEADER_USERNAME,  encode(username))
                .header(GatewayConstants.HEADER_USER_TYPE, safe(userType))
                .header(GatewayConstants.HEADER_TENANT_ID, safe(tenantId))
                .header(GatewayConstants.HEADER_ROLES,     encode(rolesStr))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private String safe(String v) { return v == null ? "" : v; }

    /** 用户名 / 角色中可能含中文，按 RFC 5987 UTF-8 URL 编码后再放入 Header */
    private String encode(String v) {
        return v == null ? "" : URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    /** 统一写回 401 JSON（Result 格式），避免 Netty 直接暴露 HTML 错误页 */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Result<Void> body = Result.failure(ResultCode.UNAUTHORIZED, message);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = ("{\"code\":401,\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /** 仅次于 RequestLogFilter：TraceId 先生成，再做鉴权 */
    @Override
    public int getOrder() {
        return -90;
    }
}
