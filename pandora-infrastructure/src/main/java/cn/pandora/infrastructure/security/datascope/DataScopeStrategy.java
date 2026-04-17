package cn.pandora.infrastructure.security.datascope;

import cn.pandora.common.security.LoginUser;

/**
 * 数据权限策略接口 —— 策略模式
 */
public interface DataScopeStrategy {

    /**
     * 生成 SQL WHERE 条件片段
     *
     * @param tableAlias     表别名（如 "u"、"d"）
     * @param columnName     字段名（如 "dept_id"、"create_by"）
     * @param user           当前登录用户
     * @return SQL 条件片段，为空表示不追加条件
     */
    String buildCondition(String tableAlias, String columnName, LoginUser user);
}
