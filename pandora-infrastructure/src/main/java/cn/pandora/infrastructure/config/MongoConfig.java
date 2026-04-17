package cn.pandora.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * MongoDB 审计开关（createdDate/lastModifiedDate 字段自动填充）
 * <p>
 * 连接串由 spring.data.mongodb.uri 自动装配；
 * pandora.mongodb.enabled = true 时启用审计。
 */
@Configuration
@EnableMongoAuditing
@ConditionalOnProperty(prefix = "pandora.mongodb", name = "enabled", havingValue = "true")
public class MongoConfig {
}
