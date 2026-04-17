package cn.pandora.auth.provider;

import cn.pandora.auth.model.*;
import cn.pandora.auth.service.AuthTokenService;
import cn.pandora.auth.service.AuthUserService;
import cn.pandora.common.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户名密码认证提供者
 */
@Component
public class UsernamePasswordAuthProvider implements AuthProvider {

    private final AuthUserService authUserService;
    private final AuthTokenService authTokenService;
    private final PasswordEncoder passwordEncoder;

    public UsernamePasswordAuthProvider(AuthUserService authUserService,
                                        AuthTokenService authTokenService,
                                        PasswordEncoder passwordEncoder) {
        this.authUserService = authUserService;
        this.authTokenService = authTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public LoginType supportedType() {
        return LoginType.USERNAME_PASSWORD;
    }

    @Override
    public LoginResponse authenticate(LoginRequest request) {
        if (request.getUsername() == null || request.getPassword() == null) {
            throw new BusinessException("用户名和密码不能为空");
        }

        AuthUser user = authUserService.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));

        if (!user.isActive()) {
            throw new BusinessException("用户已被禁用");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        AuthUserService.UserFullInfo info = authUserService.loadFullInfo(user.getId());
        return authTokenService.generateToken(user, info.roles, info.permissions, info.menus, info.dataScope);
    }
}
