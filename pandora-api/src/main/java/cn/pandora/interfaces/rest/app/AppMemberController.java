package cn.pandora.interfaces.rest.app;

import cn.pandora.common.annotation.RequiresUserType;
import cn.pandora.common.result.Result;
import cn.pandora.common.security.LoginUser;
import cn.pandora.common.security.LoginUserHolder;
import cn.pandora.common.security.UserType;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * C 端会员接口 —— 仅 C 端令牌可访问，B 端令牌被 @RequiresUserType 拦截
 * <p>
 * 路由前缀 /api/app/** → SecurityConfig 中走 C 端安全链（仅验签，无 RBAC）
 */
@RestController
@RequestMapping("/api/app/member")
@RequiresUserType(UserType.C_USER)
@RequiredArgsConstructor
public class AppMemberController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 获取当前会员信息
     */
    @GetMapping("/profile")
    public Result<MemberProfile> getProfile() {
        LoginUser user = LoginUserHolder.require();
        List<MemberProfile> list = jdbcTemplate.query(
                "SELECT id, phone, nickname, avatar, gender, email, member_level, points, " +
                        "last_login_time FROM sys_member WHERE id = ? AND deleted = 0",
                new BeanPropertyRowMapper<>(MemberProfile.class), user.getUserId());
        return Result.success(list.stream().findFirst().orElse(null));
    }

    /**
     * 更新会员资料
     */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UpdateProfileRequest req) {
        LoginUser user = LoginUserHolder.require();
        jdbcTemplate.update(
                "UPDATE sys_member SET nickname = COALESCE(?, nickname), " +
                        "avatar = COALESCE(?, avatar), gender = COALESCE(?, gender), " +
                        "email = COALESCE(?, email) WHERE id = ?",
                req.getNickname(), req.getAvatar(), req.getGender(), req.getEmail(), user.getUserId());
        return Result.success("更新成功", null);
    }

    /**
     * 获取会员等级信息
     */
    @GetMapping("/level")
    public Result<Map<String, Object>> getLevelInfo() {
        LoginUser user = LoginUserHolder.require();
        Map<String, Object> info = Map.of(
                "memberId", user.getUserId(),
                "memberLevel", user.getMemberLevel() != null ? user.getMemberLevel() : 1,
                "levelName", getLevelName(user.getMemberLevel())
        );
        return Result.success(info);
    }

    private String getLevelName(Integer level) {
        if (level == null) return "普通会员";
        return switch (level) {
            case 2 -> "银卡会员";
            case 3 -> "金卡会员";
            case 4 -> "钻石会员";
            default -> "普通会员";
        };
    }

    @Data
    public static class MemberProfile {
        private Long id;
        private String phone;
        private String nickname;
        private String avatar;
        private Integer gender;
        private String email;
        private Integer memberLevel;
        private Integer points;
        private String lastLoginTime;
    }

    @Data
    public static class UpdateProfileRequest {
        private String nickname;
        private String avatar;
        private Integer gender;
        private String email;
    }
}
