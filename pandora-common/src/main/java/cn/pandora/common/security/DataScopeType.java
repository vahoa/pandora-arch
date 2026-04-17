package cn.pandora.common.security;

import lombok.Getter;

/**
 * 数据权限范围枚举 —— 策略模式的 key
 */
@Getter
public enum DataScopeType {

    ALL(1, "全部数据"),
    DEPT_AND_CHILDREN(2, "本部门及子部门"),
    DEPT(3, "本部门"),
    SELF(4, "仅本人"),
    CUSTOM(5, "自定义部门");

    private final int code;
    private final String desc;

    DataScopeType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static DataScopeType of(int code) {
        for (DataScopeType t : values()) {
            if (t.code == code) return t;
        }
        return SELF;
    }
}
