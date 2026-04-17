package cn.pandora.infrastructure.security;

import cn.pandora.common.security.LoginUser;
import cn.pandora.common.security.LoginUserHolder;
import org.springframework.stereotype.Component;

/**
 * 权限校验 Bean —— 供 @PreAuthorize SpEL 表达式调用
 * <p>
 * B 端: @PreAuthorize("@ss.has('system:user:list')")
 * 通用: @PreAuthorize("@ss.isBUser()") / @PreAuthorize("@ss.isCUser()")
 */
@Component("ss")
public class PermissionService {

    public boolean has(String permission) {
        LoginUser user = LoginUserHolder.get();
        return user != null && user.hasPermission(permission);
    }

    public boolean hasAny(String... permissions) {
        LoginUser user = LoginUserHolder.get();
        return user != null && user.hasAnyPermission(permissions);
    }

    public boolean hasRole(String role) {
        LoginUser user = LoginUserHolder.get();
        return user != null && user.hasRole(role);
    }

    public boolean isBUser() {
        LoginUser user = LoginUserHolder.get();
        return user != null && user.isBUser();
    }

    public boolean isCUser() {
        LoginUser user = LoginUserHolder.get();
        return user != null && user.isCUser();
    }
}
