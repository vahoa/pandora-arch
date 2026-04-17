package cn.pandora.application.user.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改密码命令
 */
@Data
public class ChangePasswordCommand {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
