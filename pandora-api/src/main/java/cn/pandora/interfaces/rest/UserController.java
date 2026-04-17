package cn.pandora.interfaces.rest;

import cn.pandora.application.user.UserApplicationService;
import cn.pandora.application.user.command.ChangePasswordCommand;
import cn.pandora.application.user.command.CreateUserCommand;
import cn.pandora.application.user.dto.UserDTO;
import cn.pandora.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理 REST 控制器
 */
@Tag(name = "用户管理", description = "用户增删改查接口")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserApplicationService userApplicationService;

    public UserController(UserApplicationService userApplicationService) {
        this.userApplicationService = userApplicationService;
    }

    @Operation(summary = "创建用户")
    @PostMapping
    public Result<UserDTO> createUser(@Valid @RequestBody CreateUserCommand command) {
        UserDTO user = userApplicationService.createUser(command);
        return Result.success("用户创建成功", user);
    }

    @Operation(summary = "根据ID查询用户")
    @GetMapping("/{id}")
    public Result<UserDTO> getUser(@PathVariable Long id) {
        UserDTO user = userApplicationService.getUserById(id);
        return Result.success(user);
    }

    @Operation(summary = "根据用户名查询用户")
    @GetMapping("/by-username/{username}")
    public Result<UserDTO> getUserByUsername(@PathVariable String username) {
        UserDTO user = userApplicationService.getUserByUsername(username);
        return Result.success(user);
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordCommand command) {
        userApplicationService.changePassword(command);
        return Result.success();
    }

    @Operation(summary = "禁用用户")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @PutMapping("/{id}/disable")
    public Result<Void> disableUser(@PathVariable Long id) {
        userApplicationService.disableUser(id);
        return Result.success();
    }

    @Operation(summary = "启用用户")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @PutMapping("/{id}/enable")
    public Result<Void> enableUser(@PathVariable Long id) {
        userApplicationService.enableUser(id);
        return Result.success();
    }
}
