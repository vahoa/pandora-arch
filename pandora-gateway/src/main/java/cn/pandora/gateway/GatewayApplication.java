package cn.pandora.gateway;

import cn.pandora.gateway.config.GatewayJwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Pandora 微服务网关启动入口。
 * <p>
 * 基于 Spring Cloud Gateway 2025.1.0 WebFlux + Spring Cloud Alibaba Nacos 服务注册发现。
 * <ul>
 *   <li>{@link GatewayJwtProperties}：外部化 JWT 密钥 / 白名单 / 黑名单开关</li>
 *   <li>@EnableDiscoveryClient 已由 SCA nacos-discovery 的 AutoConfiguration 自动接管</li>
 * </ul>
 */
@SpringBootApplication
@EnableConfigurationProperties(GatewayJwtProperties.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
