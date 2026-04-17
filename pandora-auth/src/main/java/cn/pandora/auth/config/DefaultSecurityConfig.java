package cn.pandora.auth.config;

import cn.pandora.auth.service.AuthUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * 授权服务器默认安全配置
 */
@Configuration
public class DefaultSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/auth/**",
                                "/actuator/**",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/auth/**")
                )
                .formLogin(withDefaults());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(AuthUserService authUserService) {
        return username -> {
            var authUser = authUserService.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

            var roles = authUserService.getUserRoles(authUser.getId());
            String[] roleArray = roles.stream()
                    .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                    .toArray(String[]::new);

            return User.builder()
                    .username(authUser.getUsername())
                    .password(authUser.getPassword())
                    .roles(roleArray.length > 0 ? roleArray : new String[]{"USER"})
                    .disabled(!authUser.isActive())
                    .build();
        };
    }

    /**
     * 使用委派式编码器：新增密文自动打 {bcrypt} 前缀，
     * 同时识别 {noop}/{bcrypt}/{pbkdf2}/{argon2}/{scrypt} 等所有历史前缀，
     * 兼容 RegisteredClient 中以 "{noop}" 方式注册的客户端密钥。
     *
     * <p>同时通过 {@code setDefaultPasswordEncoderForMatches(BCrypt)} 指定：
     * 当数据库中密码没有任何 {id} 前缀时（如历史裸 BCrypt 密文 "$2a$..."），
     * 回退使用 BCrypt 校验，避免 PasswordMigrationRunner 早期生成的无前缀密文导致登录失败。</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder delegating =
                (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        delegating.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return delegating;
    }
}
