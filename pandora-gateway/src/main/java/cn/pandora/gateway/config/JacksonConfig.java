package cn.pandora.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关 Jackson 配置。
 * <p>
 * Spring Cloud Gateway 2025.1.0（WebFlux 栈）不会主动注册独立的 {@link ObjectMapper} Bean，
 * 因此在需要手工序列化 JSON（如 {@code AuthGlobalFilter}、{@code GatewayExceptionHandler}）的场景下自备一个，
 * 并注册 JavaTimeModule 以支持 JDK 8 时间类型。
 */
@Configuration
public class JacksonConfig {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
