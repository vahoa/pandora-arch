package cn.pandora.interfaces.base;

import cn.pandora.common.base.PageQuery;
import cn.pandora.common.result.PageResult;
import cn.pandora.common.result.Result;
import cn.pandora.infrastructure.base.BaseCrudService;
import cn.pandora.infrastructure.base.BaseDO;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通用 CRUD 控制器基类 —— 继承后自动拥有标准增删改查 + 分页接口
 * <p>
 * 使用示例（最小代码，即刻可用）：
 * <pre>
 * {@literal @}Tag(name = "订单管理")
 * {@literal @}RestController
 * {@literal @}RequestMapping("/api/orders")
 * public class OrderController extends BaseCrudController&lt;OrderService, OrderDO&gt; {
 *     // 自动拥有 CRUD + 分页，只需添加业务接口
 *     {@literal @}PostMapping("/{id}/submit")
 *     public Result&lt;Void&gt; submit(@PathVariable Long id) {
 *         baseService.submitOrder(id);
 *         return Result.success();
 *     }
 * }
 * </pre>
 *
 * @param <S> Service 类型
 * @param <D> DO 类型
 */
public abstract class BaseCrudController<S extends BaseCrudService<?, D>, D extends BaseDO> {

    @Autowired
    protected S baseService;

    @Operation(summary = "根据ID查询")
    @GetMapping("/{id}")
    public Result<D> getById(@PathVariable Long id) {
        D entity = baseService.getById(id);
        return Result.success(entity);
    }

    @Operation(summary = "新增")
    @PostMapping
    public Result<Long> create(@RequestBody D entity) {
        baseService.save(entity);
        return Result.success("创建成功", entity.getId());
    }

    @Operation(summary = "修改")
    @PutMapping
    public Result<Void> update(@RequestBody D entity) {
        baseService.updateById(entity);
        return Result.success();
    }

    @Operation(summary = "根据ID删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        baseService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "批量删除")
    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        baseService.deleteByIds(ids);
        return Result.success();
    }

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public Result<PageResult<D>> page(PageQuery query) {
        return Result.success(baseService.page(query));
    }

    @Operation(summary = "查询全部")
    @GetMapping("/list")
    public Result<List<D>> list() {
        return Result.success(baseService.list());
    }
}
