package cn.pandora.common.annotation;

import java.lang.annotation.*;

/**
 * 数据权限注解 —— 标注在 Mapper 方法或 Service 方法上，触发数据范围过滤
 * <p>
 * 使用示例：
 * <pre>
 * {@literal @}DataPermission(deptAlias = "d", userAlias = "u")
 * List&lt;UserDO&gt; selectUserList(@Param("query") UserQuery query);
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataPermission {

    /** 部门表别名，用于拼接 dept_id 条件 */
    String deptAlias() default "";

    /** 用户表别名，用于拼接 create_by 条件（仅本人数据时使用） */
    String userAlias() default "";

    /** 部门ID字段名，默认 dept_id */
    String deptIdColumn() default "dept_id";

    /** 创建人字段名，默认 create_by */
    String createByColumn() default "create_by";
}
