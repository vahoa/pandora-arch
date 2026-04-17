package cn.pandora.infrastructure.security;

import cn.pandora.common.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/**
 * 登录用户上下文过滤器 —— Token 有效性校验 + B/C 差异化上下文构建
 * <p>
 * 执行顺序（在 BearerTokenAuthenticationFilter 之后）：
 * <ol>
 *   <li>校验 jti 是否在黑名单中（登出后的 Token）</li>
 *   <li>校验 Token 代次是否过期（被强制踢出的 Token）</li>
 *   <li>以上任一命中，直接返回 401，不构建 LoginUser</li>
 *   <li>通过后，按 userType 构建 B 端或 C 端的 LoginUser</li>
 * </ol>
 */
@Slf4j
public class LoginUserFilter extends OncePerRequestFilter {

    private final PermissionCacheService permissionCacheService;
    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LoginUserFilter(PermissionCacheService permissionCacheService,
                           TokenBlacklistService tokenBlacklistService) {
        this.permissionCacheService = permissionCacheService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {

                if (isTokenRevoked(jwt)) {
                    rejectRequest(response, "令牌已失效（已登出或被踢出）");
                    return;
                }

                LoginUser loginUser = buildLoginUser(jwt);
                LoginUserHolder.set(loginUser);
            }
            chain.doFilter(request, response);
        } finally {
            LoginUserHolder.clear();
        }
    }

    private boolean isTokenRevoked(Jwt jwt) {
        String jti = jwt.getId();
        if (jti != null && tokenBlacklistService.isBlacklisted(jti)) {
            log.info("Token 已被黑名单拦截: jti={}", jti);
            return true;
        }

        Long tokenGen = jwt.hasClaim(TokenConstants.CLAIM_TOKEN_GEN)
                ? ((Number) jwt.getClaim(TokenConstants.CLAIM_TOKEN_GEN)).longValue() : null;
        if (tokenGen != null) {
            UserType userType = resolveUserType(jwt);
            String userId = userType.isAdmin()
                    ? jwt.getSubject()
                    : "c:" + jwt.getSubject();
            return tokenBlacklistService.isTokenGenExpired(userId, tokenGen);
        }

        return false;
    }

    private void rejectRequest(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = Map.of(
                "code", 401, "message", message, "timestamp", System.currentTimeMillis());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private LoginUser buildLoginUser(Jwt jwt) {
        UserType userType = resolveUserType(jwt);
        return userType.isAdmin() ? buildBUser(jwt) : buildCUser(jwt);
    }

    private LoginUser buildBUser(Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        String username = jwt.getClaimAsString("username");
        Long deptId = jwt.hasClaim("deptId") ? ((Number) jwt.getClaim("deptId")).longValue() : null;

        List<String> roles = jwt.getClaimAsStringList("roles");
        Set<String> roleSet = roles != null ? new HashSet<>(roles) : Set.of();

        if (roleSet.contains("ROLE_ADMIN")) {
            return LoginUser.builder()
                    .userType(UserType.B_USER)
                    .userId(userId).username(username).deptId(deptId)
                    .roles(roleSet).permissions(Set.of("*:*:*"))
                    .dataScope(DataScopeType.ALL)
                    .build();
        }

        Set<String> permissions = permissionCacheService.getCachedPermissions(userId);
        if (permissions.isEmpty()) {
            permissions = new HashSet<>(permissionCacheService
                    .loadFullLoginUser(userId, username, deptId).getPermissions());
        }

        Integer dataScopeCode = jwt.hasClaim("dataScope")
                ? ((Number) jwt.getClaim("dataScope")).intValue() : null;
        DataScopeType dataScope = dataScopeCode != null ? DataScopeType.of(dataScopeCode) : DataScopeType.SELF;

        return LoginUser.builder()
                .userType(UserType.B_USER)
                .userId(userId).username(username).deptId(deptId)
                .roles(roleSet).permissions(permissions).dataScope(dataScope)
                .build();
    }

    private LoginUser buildCUser(Jwt jwt) {
        Long memberId = Long.valueOf(jwt.getSubject());
        String nickname = jwt.getClaimAsString("nickname");
        String phone = jwt.getClaimAsString("phone");
        Integer memberLevel = jwt.hasClaim("memberLevel")
                ? ((Number) jwt.getClaim("memberLevel")).intValue() : 1;

        return LoginUser.builder()
                .userType(UserType.C_USER)
                .userId(memberId)
                .nickname(nickname)
                .phone(phone)
                .memberLevel(memberLevel)
                .build();
    }

    private UserType resolveUserType(Jwt jwt) {
        if (!jwt.hasClaim("userType")) return UserType.B_USER;
        return UserType.of(((Number) jwt.getClaim("userType")).intValue());
    }
}
