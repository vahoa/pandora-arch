package cn.pandora.infrastructure.security.datascope;

import cn.pandora.common.security.LoginUser;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 本部门及子部门数据
 */
public class DeptAndChildrenStrategy implements DataScopeStrategy {

    @Override
    public String buildCondition(String tableAlias, String columnName, LoginUser user) {
        Set<Long> deptIds = user.getDataScopeDeptIds();
        if (deptIds == null || deptIds.isEmpty()) {
            if (user.getDeptId() == null) return "1 = 0";
            return buildPrefix(tableAlias) + columnName + " = " + user.getDeptId();
        }
        String ids = deptIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return buildPrefix(tableAlias) + columnName + " IN (" + ids + ")";
    }

    private String buildPrefix(String alias) {
        return (alias != null && !alias.isEmpty()) ? alias + "." : "";
    }
}
