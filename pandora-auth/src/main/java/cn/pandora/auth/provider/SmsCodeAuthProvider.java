package cn.pandora.auth.provider;

import cn.pandora.auth.model.*;
import cn.pandora.auth.service.AuthTokenService;
import cn.pandora.auth.service.AuthUserService;
import cn.pandora.auth.service.SmsService;
import cn.pandora.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 手机验证码认证提供者
 */
@Component
public class SmsCodeAuthProvider implements AuthProvider {

    private final SmsService smsService;
    private final AuthUserService authUserService;
    private final AuthTokenService authTokenService;

    public SmsCodeAuthProvider(SmsService smsService, AuthUserService authUserService,
                               AuthTokenService authTokenService) {
        this.smsService = smsService;
        this.authUserService = authUserService;
        this.authTokenService = authTokenService;
    }

    @Override
    public LoginType supportedType() {
        return LoginType.SMS_CODE;
    }

    @Override
    public LoginResponse authenticate(LoginRequest request) {
        if (request.getPhone() == null || request.getSmsCode() == null) {
            throw new BusinessException("手机号和验证码不能为空");
        }

        if (!smsService.verifyCode(request.getPhone(), request.getSmsCode())) {
            throw new BusinessException("验证码错误或已过期");
        }

        // 手机号查找用户，不存在则自动注册
        AuthUser user = authUserService.findByPhone(request.getPhone())
                .orElseGet(() -> authUserService.createFromPhone(request.getPhone()));

        if (!user.isActive()) {
            throw new BusinessException("用户已被禁用");
        }

        AuthUserService.UserFullInfo info = authUserService.loadFullInfo(user.getId());
        return authTokenService.generateToken(user, info.roles, info.permissions, info.menus, info.dataScope);
    }
}
