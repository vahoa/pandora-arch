package cn.pandora.infrastructure.security.datascope;

import cn.pandora.common.security.LoginUser;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 自定义部门数据
 */
public class CustomDataScopeStrategy implements DataScopeStrategy {

    @Override
    public String buildCondition(String tableAlias, String columnName, LoginUser user) {
        Set<Long> deptIds = user.getDataScopeDeptIds();
        if (deptIds == null || deptIds.isEmpty()) return "1 = 0";
        String ids = deptIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String prefix = (tableAlias != null && !tableAlias.isEmpty()) ? tableAlias + "." : "";
        return prefix + columnName + " IN (" + ids + ")";
    }
}
