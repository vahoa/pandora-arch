package cn.pandora.auth.service;

import cn.pandora.auth.model.LoginRequest;
import cn.pandora.auth.model.LoginResponse;
import cn.pandora.auth.model.LoginType;
import cn.pandora.auth.provider.AuthProvider;
import cn.pandora.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一认证调度服务 —— 根据 loginType 分发到对应的 AuthProvider
 */
@Slf4j
@Service
public class AuthenticationService {

    private final Map<LoginType, AuthProvider> providerMap;

    public AuthenticationService(List<AuthProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(AuthProvider::supportedType, Function.identity()));
        log.info("已注册认证提供者: {}", providerMap.keySet());
    }

    public LoginResponse authenticate(LoginRequest request) {
        if (request.getLoginType() == null) {
            throw new BusinessException("登录类型不能为空");
        }

        AuthProvider provider = providerMap.get(request.getLoginType());
        if (provider == null) {
            throw new BusinessException("不支持的登录类型: " + request.getLoginType());
        }

        log.info("执行认证: type={}", request.getLoginType());
        return provider.authenticate(request);
    }
}
