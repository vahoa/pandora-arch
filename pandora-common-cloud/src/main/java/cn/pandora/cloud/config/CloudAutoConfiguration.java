package cn.pandora.cloud.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 微服务公共自动配置
 * <p>
 * 仅启用 Spring Cloud Alibaba 生态的 Sentinel 流控兜底处理。
 * 服务注册/配置由 spring-cloud-starter-alibaba-nacos-* 自动装配接管，无需显式注解。
 * 远程调用请使用 Spring Boot 原生 RestClient（同步）或 WebClient（响应式）+ Nacos NamingService 选址，
 * 或接入 Dubbo —— 严禁引入 OpenFeign 等 Spring Cloud 原生组件。
 */
@Configuration
@Import({SentinelConfig.class, JacksonLegacyConfig.class})
public class CloudAutoConfiguration {
}
