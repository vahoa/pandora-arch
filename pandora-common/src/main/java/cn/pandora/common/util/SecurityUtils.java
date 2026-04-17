package cn.pandora.common.util;

import cn.pandora.common.security.LoginUser;
import cn.pandora.common.security.LoginUserHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 安全上下文工具 —— 获取当前登录用户信息的统一入口
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<LoginUser> getLoginUser() {
        return Optional.ofNullable(LoginUserHolder.get());
    }

    public static LoginUser requireLoginUser() {
        return LoginUserHolder.require();
    }

    public static Optional<Long> getCurrentUserId() {
        return getLoginUser().map(LoginUser::getUserId);
    }

    public static Long requireCurrentUserId() {
        return requireLoginUser().getUserId();
    }

    public static Optional<String> getCurrentUsername() {
        return getLoginUser().map(LoginUser::getUsername);
    }

    public static Optional<Long> getCurrentDeptId() {
        return getLoginUser().map(LoginUser::getDeptId);
    }

    public static boolean hasPermission(String permission) {
        return getLoginUser().map(u -> u.hasPermission(permission)).orElse(false);
    }

    public static boolean hasRole(String role) {
        return getLoginUser().map(u -> u.hasRole(role)).orElse(false);
    }

    public static boolean isSuperAdmin() {
        return getLoginUser().map(LoginUser::isSuperAdmin).orElse(false);
    }

    public static boolean isAuthenticated() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::isAuthenticated)
                .orElse(false);
    }
}
