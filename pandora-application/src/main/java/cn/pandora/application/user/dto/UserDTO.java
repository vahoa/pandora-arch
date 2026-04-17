package cn.pandora.application.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户数据传输对象
 */
@Data
public class UserDTO {

    private Long id;
    private String username;
    private String email;
    private String phone;
    private String status;
    private LocalDateTime createTime;
}
