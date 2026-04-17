package cn.pandora.infrastructure.security.datascope;

import cn.pandora.common.security.LoginUser;

/**
 * 仅本人数据
 */
public class SelfDataScopeStrategy implements DataScopeStrategy {

    @Override
    public String buildCondition(String tableAlias, String columnName, LoginUser user) {
        String prefix = (tableAlias != null && !tableAlias.isEmpty()) ? tableAlias + "." : "";
        return prefix + columnName + " = " + user.getUserId();
    }
}
