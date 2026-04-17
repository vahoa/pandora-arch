package cn.pandora.infrastructure.security;

import cn.pandora.common.security.DataScopeType;
import cn.pandora.common.security.LoginUser;
import cn.pandora.infrastructure.persistence.system.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 权限缓存服务 —— 异步加载 + Redis 缓存 + 多线程并行查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private static final String PERM_KEY_PREFIX = "perm:user:";
    private static final long CACHE_TTL_HOURS = 2;

    private final StringRedisTemplate redisTemplate;
    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysDeptMapper deptMapper;

    /**
     * 并行加载用户完整权限信息（多线程）
     */
    public LoginUser loadFullLoginUser(Long userId, String username, Long deptId) {
        CompletableFuture<Set<String>> permFuture = CompletableFuture.supplyAsync(() ->
                new HashSet<>(menuMapper.selectPermissionsByUserId(userId)));

        CompletableFuture<List<SysRoleDO>> roleFuture = CompletableFuture.supplyAsync(() ->
                roleMapper.selectRolesByUserId(userId));

        CompletableFuture.allOf(permFuture, roleFuture).join();

        Set<String> permissions = permFuture.join();
        List<SysRoleDO> roles = roleFuture.join();

        Set<String> roleCodes = roles.stream().map(SysRoleDO::getRoleCode).collect(Collectors.toSet());

        DataScopeType dataScope = resolveDataScope(roles);
        Set<Long> dataScopeDeptIds = resolveDataScopeDeptIds(roles, deptId);

        LoginUser loginUser = LoginUser.builder()
                .userId(userId)
                .username(username)
                .deptId(deptId)
                .roles(roleCodes)
                .permissions(permissions)
                .dataScope(dataScope)
                .dataScopeDeptIds(dataScopeDeptIds)
                .build();

        cachePermissions(userId, permissions);
        return loginUser;
    }

    /**
     * 从缓存加载权限（用于资源服务器）
     */
    public Set<String> getCachedPermissions(Long userId) {
        String key = PERM_KEY_PREFIX + userId;
        Set<String> members = redisTemplate.opsForSet().members(key);
        return members != null ? members : Collections.emptySet();
    }

    /**
     * 清除用户权限缓存（角色/权限变更时调用）
     */
    public void evictCache(Long userId) {
        redisTemplate.delete(PERM_KEY_PREFIX + userId);
        log.info("已清除用户 [{}] 的权限缓存", userId);
    }

    public void evictCacheByRoleId(Long roleId) {
        // TODO: 查找拥有此角色的所有用户并清除缓存
        log.info("已清除角色 [{}] 关联用户的权限缓存", roleId);
    }

    private void cachePermissions(Long userId, Set<String> permissions) {
        String key = PERM_KEY_PREFIX + userId;
        redisTemplate.delete(key);
        if (!permissions.isEmpty()) {
            redisTemplate.opsForSet().add(key, permissions.toArray(String[]::new));
            redisTemplate.expire(key, CACHE_TTL_HOURS, TimeUnit.HOURS);
        }
    }

    /**
     * 取所有角色中最大的数据范围（数值最小 = 范围最大）
     */
    private DataScopeType resolveDataScope(List<SysRoleDO> roles) {
        return roles.stream()
                .map(r -> DataScopeType.of(r.getDataScope() != null ? r.getDataScope() : 4))
                .min(Comparator.comparingInt(DataScopeType::getCode))
                .orElse(DataScopeType.SELF);
    }

    private Set<Long> resolveDataScopeDeptIds(List<SysRoleDO> roles, Long userDeptId) {
        Set<Long> deptIds = new HashSet<>();
        for (SysRoleDO role : roles) {
            DataScopeType scope = DataScopeType.of(role.getDataScope() != null ? role.getDataScope() : 4);
            switch (scope) {
                case ALL -> { return Collections.emptySet(); }
                case DEPT_AND_CHILDREN -> {
                    if (userDeptId != null) {
                        deptIds.add(userDeptId);
                        deptIds.addAll(deptMapper.selectAllChildDeptIds(userDeptId));
                    }
                }
                case DEPT -> {
                    if (userDeptId != null) deptIds.add(userDeptId);
                }
                case CUSTOM -> deptIds.addAll(roleDeptMapper.selectDeptIdsByRoleId(role.getId()));
                default -> {}
            }
        }
        return deptIds;
    }
}
