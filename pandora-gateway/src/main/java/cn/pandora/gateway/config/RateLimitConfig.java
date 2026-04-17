package cn.pandora.gateway.config;

import cn.pandora.gateway.constant.GatewayConstants;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * 限流 KeyResolver 配置。
 * <p>
 * 配合 Spring Cloud Gateway 内置的 {@code RequestRateLimiter} + Redis 令牌桶（lua 原子脚本）使用。
 * <p>
 * 在路由上指定 filter：
 * <pre>
 * - name: RequestRateLimiter
 *   args:
 *     key-resolver: "#{@userKeyResolver}"
 *     redis-rate-limiter.replenishRate: 20
 *     redis-rate-limiter.burstCapacity: 40
 *     redis-rate-limiter.requestedTokens: 1
 * </pre>
 */
@Configuration
public class RateLimitConfig {

    /**
     * 用户维度限流：已登录用户优先按 userId，未登录降级按 IP。
     * 作为 {@code @Primary} 便于在未显式指定 key-resolver 时使用。
     */
    @Bean("userKeyResolver")
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_USER_ID);
            if (StringUtils.hasText(userId)) {
                return Mono.just(GatewayConstants.RATE_LIMIT_USER_PREFIX + userId);
            }
            return Mono.just(GatewayConstants.RATE_LIMIT_IP_PREFIX + resolveIp(exchange));
        };
    }

    /** IP 维度限流：登录 / 注册 / 短信验证码等接口推荐使用。 */
    @Bean("ipKeyResolver")
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(GatewayConstants.RATE_LIMIT_IP_PREFIX + resolveIp(exchange));
    }

    /** API 维度限流：接口总 QPS 保护，适合保护后端压力敏感的服务。 */
    @Bean("apiKeyResolver")
    public KeyResolver apiKeyResolver() {
        return exchange -> Mono.just(GatewayConstants.RATE_LIMIT_API_PREFIX
                + exchange.getRequest().getURI().getPath());
    }

    private String resolveIp(org.springframework.web.server.ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null ? "unknown" : remote.getAddress().getHostAddress();
    }
}
