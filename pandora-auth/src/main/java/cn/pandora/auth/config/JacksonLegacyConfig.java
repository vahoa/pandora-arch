package cn.pandora.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 2.x 兼容配置
 *
 * <p>背景：Spring Boot 4 / Spring 7 默认装配的 ObjectMapper 来自 Jackson 3.x
 * （{@code tools.jackson.databind.ObjectMapper}），而本模块内部分业务类（如微信小程序登录）
 * 仍使用 Jackson 2.x 的 {@code com.fasterxml.jackson.databind.ObjectMapper}，
 * 因此需要显式声明一个 2.x 版本的 Bean，保证依赖注入可用。</p>
 *
 * <p>注意：Jackson 2.x 的 jar（jackson-databind 2.21.x）由 jjwt-jackson 等库传递引入，
 * 无需额外依赖；本 Bean 仅用于 IOC 注入，不参与 Spring Web MVC 的序列化。</p>
 */
@Configuration
public class JacksonLegacyConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper legacyJackson2ObjectMapper() {
        return new ObjectMapper();
    }
}
