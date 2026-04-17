package cn.pandora.auth.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认证模块内部用户模型
 */
@Data
public class AuthUser {

    private Long id;
    private String username;
    private String password;
    private String email;
    private String phone;
    private Long deptId;
    private String nickname;
    private String avatar;
    private Integer status;
    private LocalDateTime createTime;

    public boolean isActive() {
        return status != null && status == 1;
    }
}
