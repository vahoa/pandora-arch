package cn.pandora.interfaces.rest.system;

import cn.pandora.common.annotation.RequiresPermission;
import cn.pandora.common.result.Result;
import cn.pandora.infrastructure.persistence.system.SysDeptDO;
import cn.pandora.infrastructure.system.SysDeptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "部门管理")
@RestController
@RequestMapping("/api/system/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService deptService;

    @Operation(summary = "部门树")
    @RequiresPermission("system:dept:list")
    @GetMapping("/tree")
    public Result<List<SysDeptDO>> tree() {
        return Result.success(deptService.listTree());
    }

    @Operation(summary = "部门详情")
    @RequiresPermission("system:dept:list")
    @GetMapping("/{id}")
    public Result<SysDeptDO> get(@PathVariable Long id) {
        return Result.success(deptService.getById(id));
    }

    @Operation(summary = "新增部门")
    @RequiresPermission("system:dept:add")
    @PostMapping
    public Result<Long> create(@RequestBody SysDeptDO dept) {
        deptService.save(dept);
        return Result.success("创建成功", dept.getId());
    }

    @Operation(summary = "修改部门")
    @RequiresPermission("system:dept:edit")
    @PutMapping
    public Result<Void> update(@RequestBody SysDeptDO dept) {
        deptService.updateById(dept);
        return Result.success();
    }

    @Operation(summary = "删除部门")
    @RequiresPermission("system:dept:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deptService.removeById(id);
        return Result.success();
    }
}
