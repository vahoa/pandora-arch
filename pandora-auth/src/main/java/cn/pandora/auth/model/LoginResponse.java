package cn.pandora.auth.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一登录响应
 * <p>
 * B 端: accessToken + userInfo（含 roles/permissions/menus/deptId）
 * C 端: accessToken + memberInfo（含 memberLevel/phone，无 RBAC 数据）
 */
@Data
@Builder
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    @Builder.Default
    private String tokenType = "Bearer";

    /** B 端用户信息（含 RBAC） */
    private UserInfo userInfo;

    /** C 端会员信息（轻量级） */
    private MemberInfo memberInfo;

    /** B 端管理员用户信息 */
    @Data
    @Builder
    public static class UserInfo {
        private Long userId;
        private String username;
        private String nickname;
        private String avatar;
        private String phone;
        private Long deptId;
        private List<String> roles;
        private Set<String> permissions;
        private List<Map<String, Object>> menus;
    }

    /** C 端消费者会员信息 */
    @Data
    @Builder
    public static class MemberInfo {
        private Long memberId;
        private String nickname;
        private String avatar;
        private String phone;
        private Integer memberLevel;
        private Integer points;
    }
}
