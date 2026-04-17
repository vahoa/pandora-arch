package cn.pandora.auth.service;

import cn.pandora.auth.config.AuthProperties;
import cn.pandora.auth.model.LoginType;
import cn.pandora.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.*;
import me.zhyd.oauth.utils.AuthStateUtils;
import org.springframework.stereotype.Service;

/**
 * 社交登录服务 —— 基于 JustAuth 统一对接各社交平台
 * <p>
 * 支持：微信APP、微信扫码、微信快速登录、微信公众号、企业微信、QQ
 */
@Slf4j
@Service
public class SocialLoginService {

    private final AuthProperties authProperties;

    public SocialLoginService(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    /**
     * 获取社交平台授权 URL
     */
    public String getAuthorizationUrl(String platform) {
        AuthRequest authRequest = buildAuthRequest(platform);
        String state = AuthStateUtils.createState();
        return authRequest.authorize(state);
    }

    /**
     * 处理社交平台回调，获取平台用户信息
     */
    @SuppressWarnings("rawtypes")
    public AuthUser handleCallback(String platform, String code, String state) {
        AuthRequest authRequest = buildAuthRequest(platform);
        AuthCallback callback = AuthCallback.builder()
                .code(code)
                .state(state)
                .build();

        AuthResponse response = authRequest.login(callback);
        if (!response.ok()) {
            log.error("社交登录回调失败: platform={}, msg={}", platform, response.getMsg());
            throw new BusinessException("社交登录失败: " + response.getMsg());
        }

        return (AuthUser) response.getData();
    }

    private AuthRequest buildAuthRequest(String platform) {
        AuthProperties.SocialConfig config = authProperties.getSocial().get(platform);
        if (config == null) {
            throw new BusinessException("未配置社交平台: " + platform);
        }

        AuthConfig authConfig = AuthConfig.builder()
                .clientId(config.getClientId())
                .clientSecret(config.getClientSecret())
                .redirectUri(config.getRedirectUri())
                .build();

        LoginType loginType = LoginType.fromCode(platform);
        return switch (loginType) {
            case WECHAT_APP, WECHAT_SCAN, WECHAT_QUICK ->
                    new AuthWeChatOpenRequest(authConfig);
            case WECHAT_OFFICIAL ->
                    new AuthWeChatMpRequest(authConfig);
            case ENTERPRISE_WECHAT ->
                    new AuthWeChatEnterpriseQrcodeRequest(authConfig);
            case QQ ->
                    new AuthQqRequest(authConfig);
            default ->
                    throw new BusinessException("不支持的社交登录平台: " + platform);
        };
    }
}
