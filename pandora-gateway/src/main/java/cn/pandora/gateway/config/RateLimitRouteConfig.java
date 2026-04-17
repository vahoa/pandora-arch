package cn.pandora.gateway.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基于 Redis 的限流 RouteLocator。
 * <p>
 * 仅当容器中存在 {@link RedisRateLimiter}（即 {@code GatewayRedisAutoConfiguration} 条件满足：有 Redis 配置时）
 * 才装配下面的路由。这样做的价值：
 * <ul>
 *   <li>无 Redis 环境 —— 网关依然可正常启动；基础路由由 YAML 定义，照常工作。</li>
 *   <li>有 Redis 环境 —— 自动挂载用户维度 / IP 维度限流，生产级防护。</li>
 * </ul>
 * <p>
 * 原则上这里的路由与 YAML 中的 {@code spring.cloud.gateway.server.webflux.routes.*}
 * 通过路由 id 区分（Java 定义的路由 id 以 {@code rl-} 前缀开头），不冲突、不覆盖。
 */
@Slf4j
@Configuration
@ConditionalOnBean(RedisRateLimiter.class)
@EnableConfigurationProperties(RateLimitRouteConfig.RateLimitProperties.class)
public class RateLimitRouteConfig {

    @Bean
    public RouteLocator rateLimitedRouteLocator(RouteLocatorBuilder builder,
                                                RedisRateLimiter redisRateLimiter,
                                                RateLimitProperties props,
                                                KeyResolver userKeyResolver,
                                                KeyResolver ipKeyResolver) {
        log.info("[Gateway] Redis 限流已启用: default={}/{}, login={}/{}",
                props.getDefaultReplenishRate(), props.getDefaultBurstCapacity(),
                props.getLoginReplenishRate(), props.getLoginBurstCapacity());

        // 两条限流专用路由，ID 与 YAML 不重复；生产上建议把 YAML 中的同名路由删掉，全部改用此处定义
        return builder.routes()
                // 业务 API：按用户维度令牌桶
                .route("rl-pandora-service-user", r -> r
                        .path("/api/users/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .requestRateLimiter(c -> {
                                    c.setRateLimiter(configuredRedisRateLimiter(
                                            props.getDefaultReplenishRate(),
                                            props.getDefaultBurstCapacity()));
                                    c.setKeyResolver(userKeyResolver);
                                }))
                        .uri("lb://pandora-service-user"))

                // 登录 / OAuth2 接口：按 IP 限流，防暴力破解
                .route("rl-pandora-auth-server", r -> r
                        .path("/auth/**", "/oauth2/**", "/.well-known/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .requestRateLimiter(c -> {
                                    c.setRateLimiter(configuredRedisRateLimiter(
                                            props.getLoginReplenishRate(),
                                            props.getLoginBurstCapacity()));
                                    c.setKeyResolver(ipKeyResolver);
                                }))
                        .uri("lb://pandora-auth-server"))
                .build();
    }

    /**
     * 快速构造一个带自定义阈值的 {@link RedisRateLimiter}。
     * 注意：spring-cloud-gateway 要求 RedisRateLimiter 实例经 InitializingBean 初始化后才能使用，
     * 这里仅作为 Route 级配置参数，不作为顶层 Bean；SCG 会在 filter 执行时自动初始化脚本。
     */
    private RedisRateLimiter configuredRedisRateLimiter(int replenishRate, int burstCapacity) {
        return new RedisRateLimiter(replenishRate, burstCapacity);
    }

    /**
     * 限流阈值外部化配置：pandora.gateway.ratelimit.*
     */
    @Data
    @ConfigurationProperties(prefix = "pandora.gateway.ratelimit")
    public static class RateLimitProperties {
        /** 默认业务 API 补充速率（tokens/s） */
        private int defaultReplenishRate = 30;
        /** 默认业务 API 峰值容量 */
        private int defaultBurstCapacity = 60;
        /** 登录 / OAuth2 的补充速率 */
        private int loginReplenishRate = 5;
        /** 登录 / OAuth2 的峰值容量 */
        private int loginBurstCapacity = 10;
    }
}
