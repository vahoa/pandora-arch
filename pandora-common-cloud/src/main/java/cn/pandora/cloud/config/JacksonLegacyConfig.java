package cn.pandora.cloud.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 2.x 兼容配置（公共 cloud 模块提供，所有微服务受益）。
 *
 * <p>背景：Spring Boot 4 / Spring 7 默认装配的 ObjectMapper 是 Jackson 3.x
 * （{@code tools.jackson.databind.ObjectMapper}），而项目内很多三方依赖（SCA、
 * 社交登录 SDK、部分业务代码）仍使用 Jackson 2.x 的
 * {@code com.fasterxml.jackson.databind.ObjectMapper}，因此需要显式声明一个 2.x
 * 版本的 Bean，保证依赖注入可用。</p>
 *
 * <p>备注：jackson-databind 2.x 由 jjwt-jackson / SCA / JustAuth 等库传递引入，
 * 本 Bean 仅用于 IOC 注入，不参与 Spring Web MVC 的序列化。</p>
 */
@Configuration
@ConditionalOnClass(ObjectMapper.class)
public class JacksonLegacyConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper legacyJackson2ObjectMapper() {
        return new ObjectMapper();
    }
}
