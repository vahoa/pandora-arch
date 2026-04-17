package cn.pandora.infrastructure.system;

import cn.pandora.common.base.PageQuery;
import cn.pandora.infrastructure.base.BaseCrudService;
import cn.pandora.infrastructure.persistence.system.SysMenuDO;
import cn.pandora.infrastructure.persistence.system.SysMenuMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SysMenuService extends BaseCrudService<SysMenuMapper, SysMenuDO> {

    @Override
    protected QueryWrapper buildQueryWrapper(PageQuery query) {
        return QueryWrapper.create().orderBy("sort", true);
    }

    /** 查询所有菜单树（管理端） */
    public List<Map<String, Object>> listTree() {
        List<SysMenuDO> all = list(QueryWrapper.create()
                .eq("status", 1).orderBy("sort", true));
        return buildTree(all, 0L);
    }

    /** 查询用户菜单树（登录后返回） */
    public List<Map<String, Object>> listTreeByUserId(Long userId) {
        List<SysMenuDO> menus = mapper.selectMenusByUserId(userId);
        return buildTree(menus, 0L);
    }

    /** 查询用户权限标识集合 */
    public Set<String> getPermissionsByUserId(Long userId) {
        return new HashSet<>(mapper.selectPermissionsByUserId(userId));
    }

    private List<Map<String, Object>> buildTree(List<SysMenuDO> list, Long parentId) {
        return list.stream()
                .filter(m -> parentId.equals(m.getParentId()))
                .map(m -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", m.getId());
                    node.put("parentId", m.getParentId());
                    node.put("menuName", m.getMenuName());
                    node.put("menuType", m.getMenuType());
                    node.put("path", m.getPath());
                    node.put("component", m.getComponent());
                    node.put("icon", m.getIcon());
                    node.put("permission", m.getPermission());
                    node.put("sort", m.getSort());
                    node.put("visible", m.getVisible());
                    List<Map<String, Object>> children = buildTree(list, m.getId());
                    if (!children.isEmpty()) node.put("children", children);
                    return node;
                })
                .collect(Collectors.toList());
    }
}
