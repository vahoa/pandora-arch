package cn.pandora.interfaces.rest.system;

import cn.pandora.common.annotation.RequiresPermission;
import cn.pandora.common.base.PageQuery;
import cn.pandora.common.result.PageResult;
import cn.pandora.common.result.Result;
import cn.pandora.infrastructure.persistence.system.SysRoleDO;
import cn.pandora.infrastructure.system.SysRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @Operation(summary = "角色分页")
    @RequiresPermission("system:role:list")
    @GetMapping("/page")
    public Result<PageResult<SysRoleDO>> page(PageQuery query) {
        return Result.success(roleService.page(query));
    }

    @Operation(summary = "角色列表")
    @RequiresPermission("system:role:list")
    @GetMapping("/list")
    public Result<List<SysRoleDO>> list() {
        return Result.success(roleService.list());
    }

    @Operation(summary = "角色详情")
    @RequiresPermission("system:role:list")
    @GetMapping("/{id}")
    public Result<SysRoleDO> get(@PathVariable Long id) {
        return Result.success(roleService.getById(id));
    }

    @Operation(summary = "新增角色")
    @RequiresPermission("system:role:add")
    @PostMapping
    public Result<Long> create(@RequestBody SysRoleDO role) {
        roleService.save(role);
        return Result.success("创建成功", role.getId());
    }

    @Operation(summary = "修改角色")
    @RequiresPermission("system:role:edit")
    @PutMapping
    public Result<Void> update(@RequestBody SysRoleDO role) {
        roleService.updateById(role);
        return Result.success();
    }

    @Operation(summary = "删除角色")
    @RequiresPermission("system:role:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "分配角色菜单权限")
    @RequiresPermission("system:role:edit")
    @PostMapping("/{roleId}/menus")
    public Result<Void> assignMenus(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        roleService.assignMenus(roleId, menuIds);
        return Result.success();
    }

    @Operation(summary = "分配数据权限范围")
    @RequiresPermission("system:role:edit")
    @PostMapping("/{roleId}/data-scope")
    public Result<Void> assignDataScope(@PathVariable Long roleId,
                                         @RequestParam Integer dataScope,
                                         @RequestBody(required = false) List<Long> deptIds) {
        roleService.assignDataScope(roleId, dataScope, deptIds);
        return Result.success();
    }

    @Operation(summary = "分配用户角色")
    @RequiresPermission("system:user:edit")
    @PostMapping("/assign-user/{userId}")
    public Result<Void> assignUserRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        roleService.assignUserRoles(userId, roleIds);
        return Result.success();
    }
}
