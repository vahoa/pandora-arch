package cn.pandora.infrastructure.base;

import cn.pandora.common.base.PageQuery;
import cn.pandora.common.result.PageResult;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;

import java.util.List;

/**
 * 通用 CRUD 服务基类（MyBatis-Flex 版）
 * <p>
 * 模板方法模式：子类覆写 {@link #buildQueryWrapper(PageQuery)} 即可定制分页查询；
 * 通过 beforeSave/afterSave/beforeUpdate/afterUpdate 钩子扩展生命周期。
 */
public abstract class BaseCrudService<M extends BaseMapper<D>, D extends BaseDO>
        extends ServiceImpl<M, D> {

    public PageResult<D> page(PageQuery query) {
        QueryWrapper wrapper = buildQueryWrapper(query);
        Page<D> page = Page.of(query.getPageNum(), query.getPageSize());
        Page<D> result = mapper.paginate(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotalRow(),
                result.getPageNumber(), result.getPageSize());
    }

    /** 钩子：构建查询条件，默认无条件 */
    protected QueryWrapper buildQueryWrapper(PageQuery query) {
        return QueryWrapper.create();
    }

    /** 钩子：保存前 */
    protected void beforeSave(D entity) {}

    /** 钩子：保存后 */
    protected void afterSave(D entity) {}

    /** 钩子：更新前 */
    protected void beforeUpdate(D entity) {}

    /** 钩子：更新后 */
    protected void afterUpdate(D entity) {}

    @Override
    public boolean save(D entity) {
        beforeSave(entity);
        boolean ok = super.save(entity);
        if (ok) afterSave(entity);
        return ok;
    }

    @Override
    public boolean updateById(D entity) {
        beforeUpdate(entity);
        boolean ok = super.updateById(entity);
        if (ok) afterUpdate(entity);
        return ok;
    }

    public boolean deleteByIds(List<Long> ids) {
        return removeByIds(ids);
    }
}
