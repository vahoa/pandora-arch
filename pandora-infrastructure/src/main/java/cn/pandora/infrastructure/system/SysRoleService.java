package cn.pandora.infrastructure.system;

import cn.pandora.common.base.PageQuery;
import cn.pandora.common.exception.BusinessException;
import cn.pandora.infrastructure.base.BaseCrudService;
import cn.pandora.infrastructure.persistence.system.*;
import cn.pandora.infrastructure.security.PermissionCacheService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SysRoleService extends BaseCrudService<SysRoleMapper, SysRoleDO> {

    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PermissionCacheService permissionCacheService;

    public SysRoleService(SysRoleMenuMapper roleMenuMapper,
                          SysRoleDeptMapper roleDeptMapper,
                          SysUserRoleMapper userRoleMapper,
                          PermissionCacheService permissionCacheService) {
        this.roleMenuMapper = roleMenuMapper;
        this.roleDeptMapper = roleDeptMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionCacheService = permissionCacheService;
    }

    @Override
    protected QueryWrapper buildQueryWrapper(PageQuery query) {
        return QueryWrapper.create().orderBy("sort", true);
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        if (getById(roleId) == null) throw new BusinessException("角色不存在");
        roleMenuMapper.deleteByRoleId(roleId);
        menuIds.forEach(menuId -> roleMenuMapper.insert(new SysRoleMenuDO(roleId, menuId)));
        permissionCacheService.evictCacheByRoleId(roleId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignDataScope(Long roleId, Integer dataScope, List<Long> deptIds) {
        SysRoleDO role = getById(roleId);
        if (role == null) throw new BusinessException("角色不存在");
        role.setDataScope(dataScope);
        updateById(role);
        roleDeptMapper.deleteByRoleId(roleId);
        if (dataScope == 5 && deptIds != null) {
            deptIds.forEach(deptId -> roleDeptMapper.insert(new SysRoleDeptDO(roleId, deptId)));
        }
        permissionCacheService.evictCacheByRoleId(roleId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignUserRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.deleteByUserId(userId);
        roleIds.forEach(roleId -> userRoleMapper.insert(new SysUserRoleDO(userId, roleId)));
        permissionCacheService.evictCache(userId);
    }

    public List<SysRoleDO> getRolesByUserId(Long userId) {
        return mapper.selectRolesByUserId(userId);
    }
}
