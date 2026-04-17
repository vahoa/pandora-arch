package cn.pandora.interfaces.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String B_USER_SCHEME = "B端管理员登录";
    private static final String C_USER_SCHEME = "C端会员Token";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DDD 架构底座 API 文档")
                        .description("基于 Spring Boot 3.2 + OAuth2 + DDD 的企业级架构\n\n"
                                + "**B 端接口** (`/api/system/**`, `/api/admin/**`): 后台管理员，完整 RBAC 鉴权\n\n"
                                + "**C 端接口** (`/api/app/**`): 前端消费者/会员，仅验证身份\n\n"
                                + "B 端: 点击 **Authorize** → 输入用户名密码（admin / admin123）\n\n"
                                + "C 端: 使用 `/auth/app/login` 获取 token 后，填入 Bearer Token")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("DDD Platform")
                                .email("admin@example.com")))
                .addSecurityItem(new SecurityRequirement().addList(B_USER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(B_USER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.OAUTH2)
                                        .description("B端管理员: 输入用户名密码，自动获取含 RBAC 的 JWT")
                                        .flows(new OAuthFlows()
                                                .password(new OAuthFlow()
                                                        .tokenUrl("/api/auth/token"))))
                        .addSecuritySchemes(C_USER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("C端会员: 使用 /auth/app/login 获取的 token")));
    }
}
