package cn.pandora.common.annotation;

import cn.pandora.common.security.UserType;

import java.lang.annotation.*;

/**
 * 用户类型准入注解 —— 防止 C 端用户访问 B 端接口，反之亦然
 * <p>
 * 使用示例：
 * <pre>
 * {@literal @}RequiresUserType(UserType.B_USER)
 * {@literal @}GetMapping("/admin/dashboard")
 * public Result&lt;?&gt; dashboard() { ... }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresUserType {

    /** 允许的用户类型 */
    UserType[] value();
}
