package cn.pandora.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis + Redisson 统一配置
 * <p>
 * - Redisson：分布式锁 / 限流 / 布隆过滤器 / 延时队列
 * - RedisTemplate：日常 kv 缓存（与 TokenBlacklistService / 权限缓存共用）
 * <p>
 * 连接参数由 spring.data.redis.* + spring.redis.redisson.* 自动装配。
 */
@Configuration
@RequiredArgsConstructor
public class RedissonConfig {

    /**
     * 以 Redisson 作为 Redis 的底层连接工厂，统一连接池与心跳。
     */
    @Bean
    @Primary
    public RedisConnectionFactory redissonConnectionFactory(RedissonClient redissonClient) {
        return new RedissonConnectionFactory(redissonClient);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
