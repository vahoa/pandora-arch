package cn.pandora.infrastructure.system;

import cn.pandora.common.base.PageQuery;
import cn.pandora.common.exception.BusinessException;
import cn.pandora.infrastructure.base.BaseCrudService;
import cn.pandora.infrastructure.persistence.system.SysDeptDO;
import cn.pandora.infrastructure.persistence.system.SysDeptMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SysDeptService extends BaseCrudService<SysDeptMapper, SysDeptDO> {

    @Override
    protected QueryWrapper buildQueryWrapper(PageQuery query) {
        return QueryWrapper.create().orderBy("sort", true);
    }

    public List<SysDeptDO> listTree() {
        List<SysDeptDO> all = list(QueryWrapper.create()
                .eq("status", 1).orderBy("sort", true));
        return buildTree(all, 0L);
    }

    /** 获取部门及所有子部门 ID */
    public List<Long> getDeptAndChildIds(Long deptId) {
        List<Long> childIds = mapper.selectAllChildDeptIds(deptId);
        childIds.add(0, deptId);
        return childIds;
    }

    @Override
    public boolean save(SysDeptDO entity) {
        if (entity.getParentId() != null && entity.getParentId() > 0) {
            SysDeptDO parent = getById(entity.getParentId());
            if (parent == null) throw new BusinessException("父部门不存在");
            entity.setAncestors(parent.getAncestors() + "," + parent.getId());
        } else {
            entity.setParentId(0L);
            entity.setAncestors("0");
        }
        return super.save(entity);
    }

    private List<SysDeptDO> buildTree(List<SysDeptDO> list, Long parentId) {
        return list.stream()
                .filter(d -> parentId.equals(d.getParentId()))
                .collect(Collectors.toList());
    }
}
