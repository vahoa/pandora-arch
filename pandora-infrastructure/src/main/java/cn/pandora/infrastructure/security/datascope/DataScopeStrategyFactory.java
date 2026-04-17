package cn.pandora.infrastructure.security.datascope;

import cn.pandora.common.security.DataScopeType;

import java.util.EnumMap;
import java.util.Map;

/**
 * 数据权限策略工厂 —— 策略模式 + 享元模式，零 new 开销
 */
public final class DataScopeStrategyFactory {

    private static final Map<DataScopeType, DataScopeStrategy> STRATEGIES = new EnumMap<>(DataScopeType.class);

    static {
        STRATEGIES.put(DataScopeType.ALL, new AllDataScopeStrategy());
        STRATEGIES.put(DataScopeType.DEPT_AND_CHILDREN, new DeptAndChildrenStrategy());
        STRATEGIES.put(DataScopeType.DEPT, new DeptDataScopeStrategy());
        STRATEGIES.put(DataScopeType.SELF, new SelfDataScopeStrategy());
        STRATEGIES.put(DataScopeType.CUSTOM, new CustomDataScopeStrategy());
    }

    private DataScopeStrategyFactory() {}

    public static DataScopeStrategy getStrategy(DataScopeType type) {
        return STRATEGIES.getOrDefault(type, STRATEGIES.get(DataScopeType.SELF));
    }
}
