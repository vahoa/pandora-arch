package cn.pandora.infrastructure.security.datascope;

import cn.pandora.common.security.LoginUser;

/**
 * 全部数据 —— 无限制
 */
public class AllDataScopeStrategy implements DataScopeStrategy {

    @Override
    public String buildCondition(String tableAlias, String columnName, LoginUser user) {
        return "";
    }
}
