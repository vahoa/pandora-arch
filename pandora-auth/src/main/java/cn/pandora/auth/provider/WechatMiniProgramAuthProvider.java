package cn.pandora.auth.provider;

import cn.pandora.auth.model.*;
import cn.pandora.auth.service.AuthTokenService;
import cn.pandora.auth.service.AuthUserService;
import cn.pandora.auth.service.WechatMiniProgramService;
import cn.pandora.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 微信小程序认证提供者
 */
@Component
public class WechatMiniProgramAuthProvider implements AuthProvider {

    private final WechatMiniProgramService miniProgramService;
    private final AuthUserService authUserService;
    private final AuthTokenService authTokenService;

    public WechatMiniProgramAuthProvider(WechatMiniProgramService miniProgramService,
                                         AuthUserService authUserService,
                                         AuthTokenService authTokenService) {
        this.miniProgramService = miniProgramService;
        this.authUserService = authUserService;
        this.authTokenService = authTokenService;
    }

    @Override
    public LoginType supportedType() {
        return LoginType.WECHAT_MINI_PROGRAM;
    }

    @Override
    public LoginResponse authenticate(LoginRequest request) {
        if (request.getWxCode() == null) {
            throw new BusinessException("微信小程序 code 不能为空");
        }

        WechatMiniProgramService.MiniProgramSession session =
                miniProgramService.code2Session(request.getWxCode());

        String platform = LoginType.WECHAT_MINI_PROGRAM.getCode();
        AuthUser user = authUserService.findBySocialBinding(platform, session.getOpenId())
                .orElseGet(() -> authUserService.createFromSocial(
                        platform, session.getOpenId(), session.getUnionId(),
                        null, null));

        AuthUserService.UserFullInfo info = authUserService.loadFullInfo(user.getId());
        return authTokenService.generateToken(user, info.roles, info.permissions, info.menus, info.dataScope);
    }
}
