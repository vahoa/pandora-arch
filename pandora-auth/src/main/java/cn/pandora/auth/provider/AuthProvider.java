package cn.pandora.auth.provider;

import cn.pandora.auth.model.LoginRequest;
import cn.pandora.auth.model.LoginResponse;
import cn.pandora.auth.model.LoginType;

/**
 * 认证提供者接口 —— 每种登录方式实现一个 Provider
 */
public interface AuthProvider {

    LoginType supportedType();

    LoginResponse authenticate(LoginRequest request);
}
