package cn.pandora.infrastructure.security;

import cn.pandora.common.security.TokenConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Token 黑名单校验服务 —— 在资源服务器侧拦截已登出/被踢出的 Token
 * <p>
 * 三层校验机制：
 * <ol>
 *   <li>jti 黑名单: 登出时 Auth 将 jti 写入 Redis，本服务检查是否命中</li>
 *   <li>Token 代次: 强制踢人时 Auth 递增用户代次，本服务比较 JWT 中的 tgen 与 Redis 中的当前代次</li>
 *   <li>以上两层任一命中，Token 视为无效，请求直接拒绝</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;

    /**
     * 检查 AccessToken 是否已被加入黑名单（登出时写入）
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null) return false;
        return Boolean.TRUE.equals(redisTemplate.hasKey(TokenConstants.BLACKLIST_PREFIX + jti));
    }

    /**
     * 检查用户的 Token 代次是否已过期（强制踢人时递增代次）
     *
     * @param userId   用户标识（B 端为 userId, C 端为 "c:{memberId}"）
     * @param tokenGen JWT 中携带的 tgen 值
     * @return true = Token 已过期（当前代次高于 JWT 中的代次）
     */
    public boolean isTokenGenExpired(String userId, Long tokenGen) {
        if (tokenGen == null) return false;
        String val = redisTemplate.opsForValue().get(TokenConstants.TOKEN_GEN_PREFIX + userId);
        if (val == null) return false;
        long currentGen = Long.parseLong(val);
        return tokenGen < currentGen;
    }
}
