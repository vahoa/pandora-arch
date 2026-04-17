package cn.pandora.infrastructure.config;

import cn.pandora.infrastructure.security.LoginUserFilter;
import cn.pandora.infrastructure.security.PermissionCacheService;
import cn.pandora.infrastructure.security.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 资源服务器安全配置 —— B/C 端双安全过滤链，完全隔离
 * <p>
 * Chain 1 (公共):   /swagger-ui/**, /api/auth/**, /api/public/** → permitAll
 * Chain 2 (B 端):   /api/admin/**, /api/system/** → JWT + RBAC + DataScope
 * Chain 3 (C 端):   /api/app/** → JWT 仅验签 + 身份认证，无 RBAC
 * Chain 4 (兜底):   其他路径 → JWT 认证
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${auth.jwt.secret:pandora-arch-jwt-secret-key-must-be-at-least-256-bits-long-2024}")
    private String jwtSecret;

    // ==================== Chain 1: 公共资源（无需认证） ====================

    @Bean
    @Order(1)
    public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                        "/actuator/**", "/api/public/**", "/api/auth/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    // ==================== Chain 2: B 端管理接口（完整 RBAC） ====================

    @Bean
    @Order(2)
    public SecurityFilterChain adminFilterChain(HttpSecurity http,
                                                PermissionCacheService permissionCacheService,
                                                TokenBlacklistService tokenBlacklistService) throws Exception {
        http
                .securityMatcher("/api/admin/**", "/api/system/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(hmacJwtDecoder())
                        .jwtAuthenticationConverter(bUserJwtConverter())))
                .addFilterAfter(new LoginUserFilter(permissionCacheService, tokenBlacklistService),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    // ==================== Chain 3: C 端消费者接口（仅验签） ====================

    @Bean
    @Order(3)
    public SecurityFilterChain appFilterChain(HttpSecurity http,
                                              PermissionCacheService permissionCacheService,
                                              TokenBlacklistService tokenBlacklistService) throws Exception {
        http
                .securityMatcher("/api/app/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(hmacJwtDecoder())
                        .jwtAuthenticationConverter(cUserJwtConverter())))
                .addFilterAfter(new LoginUserFilter(permissionCacheService, tokenBlacklistService),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    // ==================== Chain 4: 兜底（其他路径） ====================

    @Bean
    @Order(4)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http,
                                                  PermissionCacheService permissionCacheService,
                                                  TokenBlacklistService tokenBlacklistService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(hmacJwtDecoder())
                        .jwtAuthenticationConverter(bUserJwtConverter())))
                .addFilterAfter(new LoginUserFilter(permissionCacheService, tokenBlacklistService),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    // ==================== JWT 解码器（B/C 共用同一签名密钥，仅 claims 不同） ====================

    @Bean
    public JwtDecoder hmacJwtDecoder() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return token -> {
            try {
                Claims claims = Jwts.parser().verifyWith(key).build()
                        .parseSignedClaims(token).getPayload();
                Map<String, Object> headers = Map.of("alg", "HS384");
                Map<String, Object> claimsMap = new HashMap<>(claims);
                String jti = claims.getId();
                if (jti != null && !claimsMap.containsKey("jti")) {
                    claimsMap.put("jti", jti);
                }
                Instant iat = claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : Instant.now();
                Instant exp = claims.getExpiration() != null ? claims.getExpiration().toInstant() : Instant.now().plusSeconds(7200);
                return new Jwt(token, iat, exp, headers, claimsMap);
            } catch (Exception e) {
                throw new JwtException("JWT 令牌验证失败: " + e.getMessage(), e);
            }
        };
    }

    // ==================== B 端 JWT → 提取角色为 GrantedAuthority ====================

    @Bean
    public JwtAuthenticationConverter bUserJwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) return Collections.emptyList();
            return roles.stream()
                    .map(r -> new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r))
                    .collect(Collectors.toList());
        });
        return converter;
    }

    // ==================== C 端 JWT → 统一授权 ROLE_MEMBER ====================

    @Bean
    public JwtAuthenticationConverter cUserJwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt ->
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        return converter;
    }
}
