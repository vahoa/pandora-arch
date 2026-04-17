package cn.pandora.auth.controller;

import cn.pandora.auth.model.AuthUser;
import cn.pandora.auth.model.LoginRequest;
import cn.pandora.auth.model.LoginResponse;
import cn.pandora.auth.service.*;
import cn.pandora.common.exception.BusinessException;
import cn.pandora.common.result.Result;
import cn.pandora.common.security.UserType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * 统一认证控制器 —— B 端与 C 端入口完全隔离
 * <p>
 * B 端: /auth/admin/login  → 走完整 RBAC 鉴权流程
 * C 端: /auth/app/login    → 仅验证身份 + 账号状态
 * 公共: /auth/sms/send, /auth/refresh
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    private final MemberAuthService memberAuthService;
    private final SmsService smsService;
    private final SocialLoginService socialLoginService;
    private final AuthUserService authUserService;
    private final AuthMemberService authMemberService;
    private final AuthTokenService authTokenService;

    // ==================== B 端登录（后台管理员） ====================

    /**
     * B 端管理员登录 —— 返回完整 RBAC 信息（角色/权限/菜单/数据范围）
     */
    @PostMapping("/admin/login")
    public Result<LoginResponse> adminLogin(@RequestBody LoginRequest request) {
        LoginResponse response = authenticationService.authenticate(request);
        return Result.success("登录成功", response);
    }

    /**
     * 兼容旧接口，等同于 admin/login
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return adminLogin(request);
    }

    /**
     * B 端社交登录回调（PC 端微信扫码等）
     */
    @GetMapping("/admin/social/{platform}")
    public void adminSocialLogin(@PathVariable String platform,
                                 HttpServletResponse response) throws IOException {
        String authUrl = socialLoginService.getAuthorizationUrl(platform);
        response.sendRedirect(authUrl);
    }

    @GetMapping("/admin/social/callback/{platform}")
    public Result<LoginResponse> adminSocialCallback(
            @PathVariable String platform,
            @RequestParam String code,
            @RequestParam(required = false) String state) {

        me.zhyd.oauth.model.AuthUser socialUser = socialLoginService.handleCallback(platform, code, state);

        AuthUser user = authUserService.findBySocialBinding(platform, socialUser.getUuid())
                .orElseGet(() -> authUserService.createFromSocial(
                        platform, socialUser.getUuid(),
                        socialUser.getToken() != null ? socialUser.getToken().getUnionId() : null,
                        socialUser.getNickname(), socialUser.getAvatar()));

        AuthUserService.UserFullInfo info = authUserService.loadFullInfo(user.getId());
        LoginResponse response = authTokenService.generateBToken(
                user, info.roles, info.permissions, info.menus, info.dataScope);
        return Result.success("登录成功", response);
    }

    // ==================== C 端登录（前端消费者/会员） ====================

    /**
     * C 端会员登录 —— 返回轻量级令牌（无 RBAC 数据）
     */
    @PostMapping("/app/login")
    public Result<LoginResponse> appLogin(@RequestBody LoginRequest request) {
        LoginResponse response = memberAuthService.authenticate(request);
        return Result.success("登录成功", response);
    }

    /**
     * C 端社交登录回调（APP 微信、小程序等）
     */
    @GetMapping("/app/social/callback/{platform}")
    public Result<LoginResponse> appSocialCallback(
            @PathVariable String platform,
            @RequestParam String code,
            @RequestParam(required = false) String state) {

        me.zhyd.oauth.model.AuthUser socialUser = socialLoginService.handleCallback(platform, code, state);
        LoginResponse response = memberAuthService.authenticateBySocial(
                platform, socialUser.getUuid(),
                socialUser.getToken() != null ? socialUser.getToken().getUnionId() : null,
                socialUser.getNickname(), socialUser.getAvatar());
        return Result.success("登录成功", response);
    }

    // ==================== 登出（B/C 端通用） ====================

    /**
     * 登出 —— AccessToken 加入黑名单 + RefreshToken 从白名单删除
     * <p>
     * JWT 无法服务端销毁，通过 Redis 黑名单使已签发的 Token 立即失效。
     * 黑名单 TTL = Token 剩余有效期，过期后自动清理，不占用永久存储。
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authorization,
                               @RequestParam(required = false) String refreshToken) {
        String accessToken = authorization.replace("Bearer ", "");
        authTokenService.logout(accessToken, refreshToken);
        return Result.success("登出成功", null);
    }

    /**
     * 管理员强制踢人 —— 递增目标用户的 Token 代次，使其所有已签发 Token 立即失效
     */
    @PostMapping("/admin/force-logout/{userId}")
    public Result<Void> forceLogout(@PathVariable Long userId) {
        authTokenService.forceLogoutUser(String.valueOf(userId));
        return Result.success("已强制踢出用户: " + userId, null);
    }

    /**
     * 强制踢出 C 端会员
     */
    @PostMapping("/admin/force-logout/member/{memberId}")
    public Result<Void> forceLogoutMember(@PathVariable Long memberId) {
        authTokenService.forceLogoutMember(memberId);
        return Result.success("已强制踢出会员: " + memberId, null);
    }

    // ==================== 公共接口 ====================

    @PostMapping("/sms/send")
    public Result<Void> sendSmsCode(@RequestParam String phone) {
        smsService.sendCode(phone);
        return Result.success("验证码发送成功", null);
    }

    /**
     * 刷新令牌 —— RefreshToken 必须存在于 Redis 白名单中（登出后自动失效）
     * <p>
     * RefreshToken 是一次性的：使用后旧 Token 从白名单删除，签发新的。
     */
    @PostMapping("/refresh")
    public Result<LoginResponse> refreshToken(@RequestParam String refreshToken) {
        if (!authTokenService.isRefreshTokenValid(refreshToken)) {
            throw new BusinessException("刷新令牌无效、已过期或已被撤销");
        }

        var claims = authTokenService.parseToken(refreshToken);
        UserType userType = authTokenService.resolveUserType(claims);
        Long id = Long.valueOf(claims.getSubject());

        authTokenService.consumeRefreshToken(refreshToken);

        LoginResponse response;
        if (userType.isAdmin()) {
            AuthUser user = authUserService.findById(id)
                    .orElseThrow(() -> new BusinessException("用户不存在"));
            AuthUserService.UserFullInfo info = authUserService.loadFullInfo(user.getId());
            response = authTokenService.generateBToken(user, info.roles, info.permissions, info.menus, info.dataScope);
        } else {
            AuthMemberService.MemberUser member = authMemberService.findById(id)
                    .orElseThrow(() -> new BusinessException("会员不存在"));
            response = authTokenService.generateCToken(member);
        }

        return Result.success(response);
    }

    // ==================== 兼容旧社交路由 ====================

    @GetMapping("/social/{platform}")
    public void socialLogin(@PathVariable String platform, HttpServletResponse response) throws IOException {
        adminSocialLogin(platform, response);
    }

    @GetMapping("/social/callback/{platform}")
    public Result<LoginResponse> socialCallback(
            @PathVariable String platform,
            @RequestParam String code,
            @RequestParam(required = false) String state) {
        return adminSocialCallback(platform, code, state);
    }
}
