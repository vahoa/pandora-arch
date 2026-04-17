package cn.pandora.auth.model;

import lombok.Data;

/**
 * 统一登录请求 —— 同时服务 B 端和 C 端
 */
@Data
public class LoginRequest {

    private LoginType loginType;

    // ==================== 用户名密码登录（B端为主） ====================
    private String username;
    private String password;

    // ==================== 手机验证码登录（B/C端通用） ====================
    private String phone;
    private String smsCode;

    // ==================== 社交登录回调 ====================
    private String code;
    private String state;

    // ==================== 微信小程序登录（C端为主） ====================
    private String wxCode;
    private String encryptedData;
    private String iv;
}
