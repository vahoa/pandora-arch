package cn.pandora.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关 JWT 相关配置（外部化到 application.yml / Nacos）。
 */
@Data
@ConfigurationProperties(prefix = "pandora.gateway.jwt")
public class GatewayJwtProperties {

    /**
     * HMAC SHA-256 签名密钥（至少 32 字节）。线上通过环境变量 / Nacos 注入。
     */
    private String secret = "pandora-gateway-default-secret-key-change-in-production!";

    /**
     * 免鉴权路径（支持 Ant-style）。登录、OAuth2 端点、健康检查、文档等放在这里。
     */
    private List<String> whiteList = new ArrayList<>(List.of(
            "/auth/**",
            "/oauth2/**",
            "/login/**",
            "/.well-known/**",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/favicon.ico"
    ));

    /**
     * 是否开启黑名单校验（登出、踢人）。
     * 开发期可置 false 跳过 Redis；生产必须 true。
     */
    private boolean blacklistEnabled = true;
}
