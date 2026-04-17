package cn.pandora.infrastructure.security.datascope;

import cn.pandora.common.security.LoginUser;

/**
 * 本部门数据
 */
public class DeptDataScopeStrategy implements DataScopeStrategy {

    @Override
    public String buildCondition(String tableAlias, String columnName, LoginUser user) {
        if (user.getDeptId() == null) return "1 = 0";
        String prefix = (tableAlias != null && !tableAlias.isEmpty()) ? tableAlias + "." : "";
        return prefix + columnName + " = " + user.getDeptId();
    }
}
