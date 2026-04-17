package cn.pandora.auth.service;

import cn.pandora.auth.model.LoginRequest;
import cn.pandora.auth.model.LoginResponse;
import cn.pandora.auth.model.LoginType;
import cn.pandora.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * C 端会员认证服务 —— 与 B 端 AuthenticationService 完全隔离
 * <p>
 * C 端仅支持: 手机验证码、微信小程序、社交登录
 * C 端不走 RBAC，仅验证身份 + 账号状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAuthService {

    private final AuthMemberService memberService;
    private final AuthTokenService tokenService;
    private final SmsService smsService;
    private final PasswordEncoder passwordEncoder;

    /**
     * C 端统一认证入口
     */
    public LoginResponse authenticate(LoginRequest request) {
        if (request.getLoginType() == null) {
            throw new BusinessException("登录类型不能为空");
        }

        log.info("C端认证: type={}", request.getLoginType());

        return switch (request.getLoginType()) {
            case SMS_CODE -> authenticateBySms(request);
            case USERNAME_PASSWORD -> authenticateByPassword(request);
            default -> throw new BusinessException("C端不支持的登录方式: " + request.getLoginType());
        };
    }

    /**
     * 手机验证码登录（C端主流程）
     */
    private LoginResponse authenticateBySms(LoginRequest request) {
        if (request.getPhone() == null || request.getSmsCode() == null) {
            throw new BusinessException("手机号和验证码不能为空");
        }

        if (!smsService.verifyCode(request.getPhone(), request.getSmsCode())) {
            throw new BusinessException("验证码错误或已过期");
        }

        AuthMemberService.MemberUser member = memberService.findByPhone(request.getPhone())
                .orElseGet(() -> memberService.createFromPhone(request.getPhone()));

        validateMemberStatus(member);
        return tokenService.generateCToken(member);
    }

    /**
     * 手机号 + 密码登录（C端备选）
     */
    private LoginResponse authenticateByPassword(LoginRequest request) {
        if (request.getPhone() == null || request.getPassword() == null) {
            throw new BusinessException("手机号和密码不能为空");
        }

        AuthMemberService.MemberUser member = memberService.findByPhone(request.getPhone())
                .orElseThrow(() -> new BusinessException("账号或密码错误"));

        if (member.getPassword() == null || member.getPassword().isEmpty()) {
            throw new BusinessException("该账号未设置密码，请使用验证码登录");
        }

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }

        validateMemberStatus(member);
        return tokenService.generateCToken(member);
    }

    /**
     * 社交登录（C端）
     */
    public LoginResponse authenticateBySocial(String platform, String openId,
                                               String unionId, String nickname, String avatar) {
        AuthMemberService.MemberUser member = memberService.findBySocialBinding(platform, openId)
                .orElseGet(() -> memberService.createFromSocial(platform, openId, unionId, nickname, avatar));

        validateMemberStatus(member);
        return tokenService.generateCToken(member);
    }

    private void validateMemberStatus(AuthMemberService.MemberUser member) {
        if (!member.isActive()) {
            throw new BusinessException("账号已被禁用");
        }
    }
}
