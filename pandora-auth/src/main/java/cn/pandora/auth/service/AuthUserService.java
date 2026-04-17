package cn.pandora.auth.service;

import cn.pandora.auth.model.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 认证用户数据服务 —— 用户查询 + 权限加载 + Redis 缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthUserService {

    private static final String USER_FIELDS =
            "id, username, password, email, phone, dept_id, nickname, avatar, status, create_time";
    private static final String PERM_CACHE_PREFIX = "perm:user:";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public Optional<AuthUser> findByUsername(String username) {
        List<AuthUser> users = jdbcTemplate.query(
                "SELECT " + USER_FIELDS + " FROM sys_user WHERE username = ? AND deleted = 0",
                new BeanPropertyRowMapper<>(AuthUser.class), username);
        return users.stream().findFirst();
    }

    public Optional<AuthUser> findByPhone(String phone) {
        List<AuthUser> users = jdbcTemplate.query(
                "SELECT " + USER_FIELDS + " FROM sys_user WHERE phone = ? AND deleted = 0",
                new BeanPropertyRowMapper<>(AuthUser.class), phone);
        return users.stream().findFirst();
    }

    public Optional<AuthUser> findById(Long id) {
        List<AuthUser> users = jdbcTemplate.query(
                "SELECT " + USER_FIELDS + " FROM sys_user WHERE id = ? AND deleted = 0",
                new BeanPropertyRowMapper<>(AuthUser.class), id);
        return users.stream().findFirst();
    }

    public Optional<AuthUser> findBySocialBinding(String platform, String openId) {
        List<AuthUser> users = jdbcTemplate.query(
                "SELECT u." + USER_FIELDS.replace(", ", ", u.") +
                        " FROM sys_user u INNER JOIN sys_social_user s ON u.id = s.user_id " +
                        "WHERE s.platform = ? AND s.open_id = ? AND u.deleted = 0",
                new BeanPropertyRowMapper<>(AuthUser.class), platform, openId);
        return users.stream().findFirst();
    }

    public List<String> getUserRoles(Long userId) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT r.role_code FROM sys_user_role ur " +
                            "INNER JOIN sys_role r ON ur.role_id = r.id " +
                            "WHERE ur.user_id = ? AND r.status = 1 AND r.deleted = 0",
                    String.class, userId);
        } catch (Exception e) {
            return List.of("ROLE_USER");
        }
    }

    /**
     * 查询用户权限标识集合（按钮级别权限）
     */
    public Set<String> getUserPermissions(Long userId) {
        List<String> perms = jdbcTemplate.queryForList(
                "SELECT DISTINCT m.permission FROM sys_menu m " +
                        "INNER JOIN sys_role_menu rm ON m.id = rm.menu_id " +
                        "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
                        "WHERE ur.user_id = ? AND m.menu_type = 3 AND m.status = 1 AND m.deleted = 0 " +
                        "AND m.permission IS NOT NULL AND m.permission != ''",
                String.class, userId);
        return new HashSet<>(perms);
    }

    /**
     * 查询用户菜单树（供前端渲染路由）
     */
    public List<Map<String, Object>> getUserMenuTree(Long userId) {
        List<Map<String, Object>> menus = jdbcTemplate.queryForList(
                "SELECT DISTINCT m.id, m.parent_id, m.menu_name, m.menu_type, m.path, " +
                        "m.component, m.icon, m.permission, m.sort, m.visible " +
                        "FROM sys_menu m " +
                        "INNER JOIN sys_role_menu rm ON m.id = rm.menu_id " +
                        "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
                        "WHERE ur.user_id = ? AND m.menu_type IN (1, 2) AND m.status = 1 AND m.deleted = 0 " +
                        "ORDER BY m.sort",
                userId);
        return buildMenuTree(menus, 0L);
    }

    /**
     * 获取用户最高数据权限范围
     */
    public Integer getUserDataScope(Long userId) {
        try {
            List<Integer> scopes = jdbcTemplate.queryForList(
                    "SELECT r.data_scope FROM sys_role r " +
                            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
                            "WHERE ur.user_id = ? AND r.status = 1 AND r.deleted = 0",
                    Integer.class, userId);
            return scopes.stream().min(Integer::compareTo).orElse(4);
        } catch (Exception e) {
            return 4;
        }
    }

    /**
     * 多线程并行加载完整用户信息（角色+权限+菜单+数据范围）
     */
    public UserFullInfo loadFullInfo(Long userId) {
        CompletableFuture<List<String>> rolesFuture = CompletableFuture.supplyAsync(() -> getUserRoles(userId));
        CompletableFuture<Set<String>> permsFuture = CompletableFuture.supplyAsync(() -> getUserPermissions(userId));
        CompletableFuture<List<Map<String, Object>>> menusFuture = CompletableFuture.supplyAsync(() -> getUserMenuTree(userId));
        CompletableFuture<Integer> scopeFuture = CompletableFuture.supplyAsync(() -> getUserDataScope(userId));

        CompletableFuture.allOf(rolesFuture, permsFuture, menusFuture, scopeFuture).join();

        UserFullInfo info = new UserFullInfo();
        info.roles = rolesFuture.join();
        info.permissions = permsFuture.join();
        info.menus = menusFuture.join();
        info.dataScope = scopeFuture.join();

        cachePermissions(userId, info.permissions);
        return info;
    }

    /**
     * 缓存用户权限到 Redis
     */
    private void cachePermissions(Long userId, Set<String> permissions) {
        String key = PERM_CACHE_PREFIX + userId;
        try {
            redisTemplate.delete(key);
            if (!permissions.isEmpty()) {
                redisTemplate.opsForSet().add(key, permissions.toArray(String[]::new));
                redisTemplate.expire(key, 2, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.warn("权限缓存写入失败: {}", e.getMessage());
        }
    }

    public AuthUser createFromSocial(String platform, String openId, String unionId,
                                     String nickname, String avatar) {
        String username = platform + "_" + openId.substring(0, Math.min(openId.length(), 8));
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password, email, phone, nickname, avatar, status, deleted) " +
                        "VALUES (?, '', '', '', ?, ?, 1, 0)",
                username, nickname != null ? nickname : username, avatar);
        AuthUser user = findByUsername(username)
                .orElseThrow(() -> new RuntimeException("创建社交用户失败"));
        jdbcTemplate.update(
                "INSERT INTO sys_social_user (user_id, platform, open_id, union_id, nickname, avatar) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                user.getId(), platform, openId, unionId, nickname, avatar);
        return user;
    }

    public AuthUser createFromPhone(String phone) {
        String username = "phone_" + phone.substring(phone.length() - 4);
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password, email, phone, nickname, avatar, status, deleted) " +
                        "VALUES (?, '', '', ?, ?, '', 1, 0)",
                username, phone, username);
        return findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("创建手机用户失败"));
    }

    private List<Map<String, Object>> buildMenuTree(List<Map<String, Object>> list, Long parentId) {
        List<Map<String, Object>> tree = new ArrayList<>();
        for (Map<String, Object> menu : list) {
            Object pid = menu.get("parent_id");
            Long menuParentId = pid instanceof Number ? ((Number) pid).longValue() : 0L;
            if (parentId.equals(menuParentId)) {
                Map<String, Object> node = new LinkedHashMap<>(menu);
                List<Map<String, Object>> children = buildMenuTree(list, ((Number) menu.get("id")).longValue());
                if (!children.isEmpty()) node.put("children", children);
                tree.add(node);
            }
        }
        return tree;
    }

    public static class UserFullInfo {
        public List<String> roles;
        public Set<String> permissions;
        public List<Map<String, Object>> menus;
        public Integer dataScope;
    }
}
