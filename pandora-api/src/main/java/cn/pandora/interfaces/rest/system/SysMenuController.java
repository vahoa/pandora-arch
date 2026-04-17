package cn.pandora.interfaces.rest.system;

import cn.pandora.common.annotation.RequiresPermission;
import cn.pandora.common.result.Result;
import cn.pandora.common.util.SecurityUtils;
import cn.pandora.infrastructure.persistence.system.SysMenuDO;
import cn.pandora.infrastructure.system.SysMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "菜单管理")
@RestController
@RequestMapping("/api/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    @Operation(summary = "菜单树（管理端）")
    @RequiresPermission("system:menu:list")
    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> tree() {
        return Result.success(menuService.listTree());
    }

    @Operation(summary = "当前用户菜单树（前端路由）")
    @GetMapping("/user-tree")
    public Result<List<Map<String, Object>>> userTree() {
        Long userId = SecurityUtils.requireCurrentUserId();
        return Result.success(menuService.listTreeByUserId(userId));
    }

    @Operation(summary = "菜单详情")
    @RequiresPermission("system:menu:list")
    @GetMapping("/{id}")
    public Result<SysMenuDO> get(@PathVariable Long id) {
        return Result.success(menuService.getById(id));
    }

    @Operation(summary = "新增菜单")
    @RequiresPermission("system:menu:add")
    @PostMapping
    public Result<Long> create(@RequestBody SysMenuDO menu) {
        menuService.save(menu);
        return Result.success("创建成功", menu.getId());
    }

    @Operation(summary = "修改菜单")
    @RequiresPermission("system:menu:edit")
    @PutMapping
    public Result<Void> update(@RequestBody SysMenuDO menu) {
        menuService.updateById(menu);
        return Result.success();
    }

    @Operation(summary = "删除菜单")
    @RequiresPermission("system:menu:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.removeById(id);
        return Result.success();
    }
}
