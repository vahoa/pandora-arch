package cn.pandora.domain.user;

/**
 * 用户状态枚举
 */
public enum UserStatus {

    ACTIVE(1, "启用"),
    DISABLED(0, "禁用");

    private final int code;
    private final String description;

    UserStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static UserStatus fromCode(int code) {
        for (UserStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的用户状态码: " + code);
    }
}
