package cn.pandora.auth.model;

/**
 * 登录类型枚举 —— 支持 9 种登录方式
 */
public enum LoginType {

    USERNAME_PASSWORD("username_password", "用户名密码登录"),
    SMS_CODE("sms_code", "手机验证码登录"),
    WECHAT_APP("wechat_app", "微信APP登录"),
    WECHAT_SCAN("wechat_scan", "微信扫码登录"),
    WECHAT_QUICK("wechat_quick", "微信快速登录"),
    WECHAT_MINI_PROGRAM("wechat_mini_program", "微信小程序登录"),
    WECHAT_OFFICIAL("wechat_official", "微信公众号登录"),
    ENTERPRISE_WECHAT("enterprise_wechat", "企业微信登录"),
    QQ("qq", "QQ登录");

    private final String code;
    private final String description;

    LoginType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static LoginType fromCode(String code) {
        for (LoginType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的登录类型: " + code);
    }
}
