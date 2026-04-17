package cn.pandora.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 多渠道认证配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private Jwt jwt = new Jwt();
    private Sms sms = new Sms();
    private WechatMini wechatMini = new WechatMini();
    private Map<String, SocialConfig> social = new HashMap<>();

    @Data
    public static class Jwt {
        private String secret = "pandora-arch-jwt-secret-key-must-be-at-least-256-bits-long-2024";
        private Long expiration = 7200L;
        private Long refreshExpiration = 604800L;
    }

    @Data
    public static class Sms {
        private Integer codeLength = 6;
        private Integer expireMinutes = 5;
        private Integer dailyLimit = 10;
        /** 开发环境开启模拟模式，验证码固定为 888888 */
        private Boolean mockEnabled = true;
    }

    @Data
    public static class WechatMini {
        private String appId;
        private String appSecret;
    }

    @Data
    public static class SocialConfig {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
    }
}
