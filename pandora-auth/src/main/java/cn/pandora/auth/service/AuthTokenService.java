package cn.pandora.auth.service;

import cn.pandora.auth.config.AuthProperties;
import cn.pandora.auth.model.AuthUser;
import cn.pandora.auth.model.LoginResponse;
import cn.pandora.common.security.TokenConstants;
import cn.pandora.common.security.UserType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * JWT 令牌服务 —— B/C 端差异化令牌 + Token 生命周期管理
 * <p>
 * 核心机制（解决 JWT 无法服务端销毁的问题）：
 * <ol>
 *   <li>每个 JWT 携带唯一 jti（JWT ID）+ tgen（Token 代次）</li>
 *   <li>AccessToken: 登出时将 jti 加入 Redis 黑名单，TTL = 剩余有效期</li>
 *   <li>RefreshToken: 签发时存入 Redis 白名单，登出时删除，刷新时必须命中白名单</li>
 *   <li>强制踢人: 递增用户 Token 代次，所有 tgen 低于当前代次的 Token 自动失效</li>
 * </ol>
 */
@Slf4j
@Service
public class AuthTokenService {

    private static final long C_USER_ACCESS_EXPIRATION = 7 * 24 * 3600L;
    private static final long C_USER_REFRESH_EXPIRATION = 30 * 24 * 3600L;

    private final AuthProperties authProperties;
    private final SecretKey signingKey;
    private final StringRedisTemplate redisTemplate;

    public AuthTokenService(AuthProperties authProperties, StringRedisTemplate redisTemplate) {
        this.authProperties = authProperties;
        this.signingKey = Keys.hmacShaKeyFor(
                authProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.redisTemplate = redisTemplate;
    }

    // ==================== B 端令牌（完整 RBAC） ====================

    public LoginResponse generateBToken(AuthUser user, List<String> roles,
                                         Set<String> permissions,
                                         List<Map<String, Object>> menus,
                                         Integer dataScope) {
        long now = System.currentTimeMillis();
        long expirationSec = authProperties.getJwt().getExpiration();
        long refreshExpirationSec = authProperties.getJwt().getRefreshExpiration();
        String userId = String.valueOf(user.getId());
        long tokenGen = getCurrentTokenGen(userId);

        String accessJti = UUID.randomUUID().toString();
        String accessToken = Jwts.builder()
                .id(accessJti)
                .subject(userId)
                .claim("userType", UserType.B_USER.getCode())
                .claim("username", user.getUsername())
                .claim("deptId", user.getDeptId())
                .claim("roles", roles)
                .claim("dataScope", dataScope)
                .claim(TokenConstants.CLAIM_TOKEN_GEN, tokenGen)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationSec * 1000))
                .signWith(signingKey)
                .compact();

        String refreshJti = UUID.randomUUID().toString();
        String refreshToken = buildRefreshToken(refreshJti, userId, UserType.B_USER,
                refreshExpirationSec * 1000, tokenGen);
        storeRefreshToken(refreshJti, userId, refreshExpirationSec);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expirationSec)
                .tokenType("Bearer")
                .userInfo(LoginResponse.UserInfo.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .nickname(user.getNickname())
                        .avatar(user.getAvatar())
                        .phone(user.getPhone())
                        .deptId(user.getDeptId())
                        .roles(roles)
                        .permissions(permissions)
                        .menus(menus)
                        .build())
                .build();
    }

    // ==================== C 端令牌（轻量级，无 RBAC） ====================

    public LoginResponse generateCToken(AuthMemberService.MemberUser member) {
        long now = System.currentTimeMillis();
        String memberId = String.valueOf(member.getId());
        long tokenGen = getCurrentTokenGen("c:" + memberId);

        String accessJti = UUID.randomUUID().toString();
        String accessToken = Jwts.builder()
                .id(accessJti)
                .subject(memberId)
                .claim("userType", UserType.C_USER.getCode())
                .claim("nickname", member.getNickname())
                .claim("phone", member.getPhone())
                .claim("memberLevel", member.getMemberLevel())
                .claim(TokenConstants.CLAIM_TOKEN_GEN, tokenGen)
                .issuedAt(new Date(now))
                .expiration(new Date(now + C_USER_ACCESS_EXPIRATION * 1000))
                .signWith(signingKey)
                .compact();

        String refreshJti = UUID.randomUUID().toString();
        String refreshToken = buildRefreshToken(refreshJti, memberId, UserType.C_USER,
                C_USER_REFRESH_EXPIRATION * 1000, tokenGen);
        storeRefreshToken(refreshJti, "c:" + memberId, C_USER_REFRESH_EXPIRATION);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(C_USER_ACCESS_EXPIRATION)
                .tokenType("Bearer")
                .memberInfo(LoginResponse.MemberInfo.builder()
                        .memberId(member.getId())
                        .nickname(member.getNickname())
                        .avatar(member.getAvatar())
                        .phone(member.getPhone())
                        .memberLevel(member.getMemberLevel())
                        .points(member.getPoints())
                        .build())
                .build();
    }

    // ==================== 登出：AccessToken 加入黑名单 + RefreshToken 从白名单删除 ====================

    /**
     * 登出 —— 使 AccessToken 和 RefreshToken 同时失效
     */
    public void logout(String accessToken, String refreshToken) {
        try {
            Claims accessClaims = parseToken(accessToken);
            String accessJti = accessClaims.getId();
            if (accessJti != null) {
                long remainingMs = accessClaims.getExpiration().getTime() - System.currentTimeMillis();
                if (remainingMs > 0) {
                    redisTemplate.opsForValue().set(
                            TokenConstants.BLACKLIST_PREFIX + accessJti, "1",
                            remainingMs, TimeUnit.MILLISECONDS);
                    log.info("AccessToken 已加入黑名单: jti={}", accessJti);
                }
            }
        } catch (Exception e) {
            log.warn("AccessToken 解析失败（可能已过期）: {}", e.getMessage());
        }

        if (refreshToken != null) {
            try {
                Claims refreshClaims = parseToken(refreshToken);
                String refreshJti = refreshClaims.getId();
                if (refreshJti != null) {
                    redisTemplate.delete(TokenConstants.REFRESH_PREFIX + refreshJti);
                    log.info("RefreshToken 已从白名单移除: jti={}", refreshJti);
                }
            } catch (Exception e) {
                log.warn("RefreshToken 解析失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 强制踢人 —— 递增用户 Token 代次，所有旧 Token 自动失效
     */
    public void forceLogoutUser(String userId) {
        String key = TokenConstants.TOKEN_GEN_PREFIX + userId;
        redisTemplate.opsForValue().increment(key);
        log.info("已强制踢出用户: {}, 所有旧 Token 已失效", userId);
    }

    /**
     * 强制踢出 C 端会员
     */
    public void forceLogoutMember(Long memberId) {
        forceLogoutUser("c:" + memberId);
    }

    // ==================== RefreshToken 校验（必须命中白名单） ====================

    /**
     * 校验 RefreshToken 是否仍在白名单中（登出后会被移除）
     */
    public boolean isRefreshTokenValid(String refreshToken) {
        try {
            Claims claims = parseToken(refreshToken);
            String jti = claims.getId();
            if (jti == null) return false;
            return Boolean.TRUE.equals(redisTemplate.hasKey(TokenConstants.REFRESH_PREFIX + jti));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 使用 RefreshToken 后，将旧的从白名单删除（一次性使用）
     */
    public void consumeRefreshToken(String refreshToken) {
        try {
            Claims claims = parseToken(refreshToken);
            String jti = claims.getId();
            if (jti != null) {
                redisTemplate.delete(TokenConstants.REFRESH_PREFIX + jti);
            }
        } catch (Exception ignored) {}
    }

    // ==================== 向后兼容 ====================

    public LoginResponse generateToken(AuthUser user, List<String> roles,
                                        Set<String> permissions,
                                        List<Map<String, Object>> menus,
                                        Integer dataScope) {
        return generateBToken(user, roles, permissions, menus, dataScope);
    }

    public LoginResponse generateToken(AuthUser user, List<String> roles) {
        return generateBToken(user, roles, Collections.emptySet(), Collections.emptyList(), 1);
    }

    // ==================== 公共方法 ====================

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(token).getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public UserType resolveUserType(Claims claims) {
        Integer code = claims.get("userType", Integer.class);
        return code != null ? UserType.of(code) : UserType.B_USER;
    }

    // ==================== 内部方法 ====================

    private String buildRefreshToken(String jti, String subject, UserType userType,
                                     long expirationMs, long tokenGen) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .id(jti)
                .subject(subject)
                .claim("type", "refresh")
                .claim("userType", userType.getCode())
                .claim(TokenConstants.CLAIM_TOKEN_GEN, tokenGen)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    private void storeRefreshToken(String jti, String userId, long ttlSeconds) {
        redisTemplate.opsForValue().set(
                TokenConstants.REFRESH_PREFIX + jti, userId,
                ttlSeconds, TimeUnit.SECONDS);
    }

    private long getCurrentTokenGen(String userId) {
        String val = redisTemplate.opsForValue().get(TokenConstants.TOKEN_GEN_PREFIX + userId);
        return val != null ? Long.parseLong(val) : 0L;
    }
}
