package cn.pandora.common.security;

/**
 * Token 生命周期管理常量 —— Auth 模块和 Infrastructure 模块共享
 * <p>
 * JWT 是无状态的，无法服务端"销毁"。企业级方案通过三层 Redis 机制实现 Token 失效：
 * <ol>
 *   <li><b>AccessToken 黑名单</b>: 登出时将 jti 写入 Redis，LoginUserFilter 拦截黑名单中的 Token</li>
 *   <li><b>RefreshToken 白名单</b>: 签发时存入 Redis，登出时删除，刷新时校验存在性</li>
 *   <li><b>用户 Token 代次</b>: 管理员踢人时递增代次，低于当前代次的所有 Token 自动失效</li>
 * </ol>
 */
public final class TokenConstants {

    private TokenConstants() {}

    /** AccessToken 黑名单前缀: token:blacklist:{jti} → "1", TTL = Token 剩余有效期 */
    public static final String BLACKLIST_PREFIX = "token:blacklist:";

    /** RefreshToken 白名单前缀: token:refresh:{jti} → userId, TTL = RefreshToken 有效期 */
    public static final String REFRESH_PREFIX = "token:refresh:";

    /** 用户 Token 代次前缀: token:gen:{userId} → 代次号（Long），无 TTL */
    public static final String TOKEN_GEN_PREFIX = "token:gen:";

    /** JWT 中的 jti claim 名 */
    public static final String CLAIM_JTI = "jti";

    /** JWT 中的 Token 代次 claim 名 */
    public static final String CLAIM_TOKEN_GEN = "tgen";
}
