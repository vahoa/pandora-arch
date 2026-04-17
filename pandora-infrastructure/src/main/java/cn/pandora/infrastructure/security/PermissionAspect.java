package cn.pandora.infrastructure.security;

import cn.pandora.common.annotation.RequiresPermission;
import cn.pandora.common.annotation.RequiresUserType;
import cn.pandora.common.exception.BusinessException;
import cn.pandora.common.security.LoginUser;
import cn.pandora.common.security.LoginUserHolder;
import cn.pandora.common.security.UserType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 权限校验切面 —— 拦截 @RequiresPermission 和 @RequiresUserType，B/C 端差异化鉴权
 * <p>
 * @RequiresPermission: 仅对 B 端用户生效（RBAC 权限检查）
 * @RequiresUserType: B/C 端准入控制（防止 C 端令牌访问 B 端接口）
 */
@Slf4j
@Aspect
@Component
public class PermissionAspect {

    /**
     * B 端 RBAC 权限校验 —— C 端用户直接拒绝
     */
    @Around("@annotation(rp)")
    public Object checkPermission(ProceedingJoinPoint pjp, RequiresPermission rp) throws Throwable {
        LoginUser user = LoginUserHolder.get();
        if (user == null) {
            throw new BusinessException("未登录或令牌已过期");
        }

        if (user.isCUser()) {
            throw new BusinessException("C端用户无权访问管理接口");
        }

        String perm = rp.value();
        if (!user.hasPermission(perm)) {
            log.warn("权限拒绝 -> 用户: {}, 缺少权限: {}", user.getUsername(), perm);
            throw new BusinessException("没有操作权限: " + perm);
        }

        return pjp.proceed();
    }

    /**
     * 用户类型准入校验 —— 防止跨端访问（支持类级别和方法级别注解）
     */
    @Around("@within(cn.pandora.common.annotation.RequiresUserType) || " +
            "@annotation(cn.pandora.common.annotation.RequiresUserType)")
    public Object checkUserType(ProceedingJoinPoint pjp) throws Throwable {
        LoginUser user = LoginUserHolder.get();
        if (user == null) {
            throw new BusinessException("未登录或令牌已过期");
        }

        RequiresUserType rut = resolveUserTypeAnnotation(pjp);
        if (rut == null) {
            return pjp.proceed();
        }

        UserType[] allowed = rut.value();
        boolean match = Arrays.stream(allowed).anyMatch(t -> t == user.getUserType());
        if (!match) {
            log.warn("用户类型拒绝 -> {}, 需要: {}", user.getUserType(), Arrays.toString(allowed));
            throw new BusinessException("无权访问此接口");
        }

        return pjp.proceed();
    }

    private RequiresUserType resolveUserTypeAnnotation(ProceedingJoinPoint pjp) {
        org.aspectj.lang.reflect.MethodSignature ms =
                (org.aspectj.lang.reflect.MethodSignature) pjp.getSignature();
        RequiresUserType rut = ms.getMethod().getAnnotation(RequiresUserType.class);
        if (rut == null) {
            rut = pjp.getTarget().getClass().getAnnotation(RequiresUserType.class);
        }
        return rut;
    }
}
