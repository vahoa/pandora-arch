package cn.pandora.common.security;

import lombok.Getter;

/**
 * 用户类型枚举 —— B端与C端完全隔离的基石
 * <p>
 * B端: 后台管理员，走完整 RBAC 鉴权（角色/权限/菜单/数据范围）
 * C端: 前端消费者/会员，仅验证身份 + 账号状态，不参与 RBAC
 */
@Getter
public enum UserType {

    B_USER(1, "B端后台用户"),
    C_USER(2, "C端消费者用户");

    private final int code;
    private final String desc;

    UserType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static UserType of(int code) {
        for (UserType t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("未知用户类型: " + code);
    }

    public boolean isAdmin() {
        return this == B_USER;
    }

    public boolean isConsumer() {
        return this == C_USER;
    }
}
