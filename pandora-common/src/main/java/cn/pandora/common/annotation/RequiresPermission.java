package cn.pandora.common.annotation;

import java.lang.annotation.*;

/**
 * API / 按钮级别权限校验注解
 * <p>
 * 使用示例：
 * <pre>
 * {@literal @}RequiresPermission("system:user:add")
 * {@literal @}PostMapping
 * public Result&lt;Long&gt; create(@RequestBody UserDTO dto) { ... }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

    /** 权限标识，如 "system:user:add" */
    String value();

    /** 多权限时的逻辑关系，默认 AND */
    Logical logical() default Logical.AND;

    enum Logical { AND, OR }
}
