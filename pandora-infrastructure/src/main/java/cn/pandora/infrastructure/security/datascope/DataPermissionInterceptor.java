package cn.pandora.infrastructure.security.datascope;

import cn.pandora.common.annotation.DataPermission;
import cn.pandora.common.security.DataScopeType;
import cn.pandora.common.security.LoginUser;
import cn.pandora.common.security.LoginUserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据权限拦截器（标准 MyBatis Interceptor）
 * <p>
 * 通过 {@link DataPermission} 注解标记需要隔离的 Mapper 方法，
 * 基于当前 LoginUser 的 DataScope 在 SELECT SQL 末尾追加 WHERE 条件。
 */
@Slf4j
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare",
                args = {Connection.class, Integer.class})
})
public class DataPermissionInterceptor implements Interceptor {

    private final Map<String, DataPermission> annotationCache = new ConcurrentHashMap<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        MetaObject meta = SystemMetaObject.forObject(handler);
        MappedStatement ms = (MappedStatement) meta.getValue("delegate.mappedStatement");

        if (ms.getSqlCommandType() != SqlCommandType.SELECT) {
            return invocation.proceed();
        }

        LoginUser user = LoginUserHolder.get();
        if (user == null || user.isSuperAdmin()) return invocation.proceed();

        DataPermission annotation = getAnnotation(ms.getId());
        if (annotation == null) return invocation.proceed();

        DataScopeType scopeType = user.getDataScope();
        if (scopeType == null || scopeType == DataScopeType.ALL) return invocation.proceed();

        String condition = buildCondition(annotation, scopeType, user);
        if (condition == null || condition.isEmpty()) return invocation.proceed();

        String originalSql = (String) meta.getValue("delegate.boundSql.sql");
        String newSql = originalSql + " AND " + condition;
        meta.setValue("delegate.boundSql.sql", newSql);

        log.debug("数据权限过滤 -> 用户: {}, 范围: {}, 条件: {}", user.getUsername(), scopeType, condition);
        return invocation.proceed();
    }

    private String buildCondition(DataPermission dp, DataScopeType scopeType, LoginUser user) {
        DataScopeStrategy strategy = DataScopeStrategyFactory.getStrategy(scopeType);
        if (scopeType == DataScopeType.SELF) {
            return strategy.buildCondition(dp.userAlias(), dp.createByColumn(), user);
        }
        return strategy.buildCondition(dp.deptAlias(), dp.deptIdColumn(), user);
    }

    private DataPermission getAnnotation(String msId) {
        return annotationCache.computeIfAbsent(msId, id -> {
            try {
                String className = id.substring(0, id.lastIndexOf('.'));
                String methodName = id.substring(id.lastIndexOf('.') + 1);
                Class<?> clazz = Class.forName(className);

                DataPermission classAnno = clazz.getAnnotation(DataPermission.class);

                for (Method method : clazz.getMethods()) {
                    if (method.getName().equals(methodName)) {
                        DataPermission methodAnno = method.getAnnotation(DataPermission.class);
                        return methodAnno != null ? methodAnno : classAnno;
                    }
                }
                return classAnno;
            } catch (Exception e) {
                return null;
            }
        });
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
