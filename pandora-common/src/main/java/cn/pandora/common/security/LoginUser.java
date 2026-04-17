package cn.pandora.common.security;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

/**
 * 当前登录用户上下文 —— 贯穿所有层的安全载体
 * <p>
 * B端用户: 携带完整 RBAC 信息（roles/permissions/dataScope）
 * C端用户: 仅携带身份信息（userId/nickname/phone/memberLevel），RBAC 字段为空
 */
@Data
@Builder
public class LoginUser implements Serializable {

    /** 用户类型：B端 or C端，决定鉴权策略 */
    @Builder.Default
    private UserType userType = UserType.B_USER;

    private Long userId;
    private String username;
    private String nickname;
    private String phone;

    // ==================== B端专属字段 ====================
    private Long deptId;

    @Builder.Default
    private Set<String> roles = Collections.emptySet();

    @Builder.Default
    private Set<String> permissions = Collections.emptySet();

    private DataScopeType dataScope;

    @Builder.Default
    private Set<Long> dataScopeDeptIds = Collections.emptySet();

    // ==================== C端专属字段 ====================
    /** 会员等级（仅C端） */
    private Integer memberLevel;

    // ==================== 身份判断 ====================

    public boolean isBUser() {
        return userType == UserType.B_USER;
    }

    public boolean isCUser() {
        return userType == UserType.C_USER;
    }

    public boolean isSuperAdmin() {
        return isBUser() && roles.contains("ROLE_ADMIN");
    }

    // ==================== 权限判断（仅B端有效） ====================

    public boolean hasPermission(String perm) {
        if (!isBUser()) return false;
        return isSuperAdmin() || permissions.contains(perm);
    }

    public boolean hasAnyPermission(String... perms) {
        if (!isBUser()) return false;
        if (isSuperAdmin()) return true;
        for (String p : perms) {
            if (permissions.contains(p)) return true;
        }
        return false;
    }

    public boolean hasRole(String role) {
        if (!isBUser()) return false;
        return roles.contains(role.startsWith("ROLE_") ? role : "ROLE_" + role);
    }
}
