package cn.pandora.auth.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * C 端会员数据服务 —— 操作 sys_member 表，与 B 端 sys_user 完全隔离
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthMemberService {

    private static final String MEMBER_FIELDS =
            "id, phone, password, nickname, avatar, gender, email, member_level, points, status, last_login_time";

    private final JdbcTemplate jdbcTemplate;

    public Optional<MemberUser> findByPhone(String phone) {
        List<MemberUser> list = jdbcTemplate.query(
                "SELECT " + MEMBER_FIELDS + " FROM sys_member WHERE phone = ? AND deleted = 0",
                new BeanPropertyRowMapper<>(MemberUser.class), phone);
        return list.stream().findFirst();
    }

    public Optional<MemberUser> findById(Long id) {
        List<MemberUser> list = jdbcTemplate.query(
                "SELECT " + MEMBER_FIELDS + " FROM sys_member WHERE id = ? AND deleted = 0",
                new BeanPropertyRowMapper<>(MemberUser.class), id);
        return list.stream().findFirst();
    }

    public Optional<MemberUser> findBySocialBinding(String platform, String openId) {
        List<MemberUser> list = jdbcTemplate.query(
                "SELECT m." + MEMBER_FIELDS.replace("id,", "m.id,") +
                        " FROM sys_member m INNER JOIN sys_member_social s ON m.id = s.member_id " +
                        "WHERE s.platform = ? AND s.open_id = ? AND m.deleted = 0",
                new BeanPropertyRowMapper<>(MemberUser.class), platform, openId);
        return list.stream().findFirst();
    }

    /**
     * 通过手机号自动注册 C 端会员
     */
    public MemberUser createFromPhone(String phone) {
        jdbcTemplate.update(
                "INSERT INTO sys_member (phone, nickname, register_source) VALUES (?, ?, 'phone')",
                phone, "用户" + phone.substring(phone.length() - 4));
        return findByPhone(phone).orElseThrow(() -> new RuntimeException("创建会员失败"));
    }

    /**
     * 通过社交平台自动注册 C 端会员
     */
    public MemberUser createFromSocial(String platform, String openId, String unionId,
                                       String nickname, String avatar) {
        String phone = null;
        jdbcTemplate.update(
                "INSERT INTO sys_member (phone, nickname, avatar, register_source) VALUES (?, ?, ?, ?)",
                phone, nickname != null ? nickname : "社交用户", avatar, platform);

        Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "INSERT INTO sys_member_social (member_id, platform, open_id, union_id, nickname, avatar) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                memberId, platform, openId, unionId, nickname, avatar);

        return findById(memberId).orElseThrow(() -> new RuntimeException("创建社交会员失败"));
    }

    /**
     * 更新最后登录信息
     */
    public void updateLoginInfo(Long memberId, String ip) {
        jdbcTemplate.update(
                "UPDATE sys_member SET last_login_time = NOW(), last_login_ip = ? WHERE id = ?",
                ip, memberId);
    }

    @Data
    public static class MemberUser {
        private Long id;
        private String phone;
        private String password;
        private String nickname;
        private String avatar;
        private Integer gender;
        private String email;
        private Integer memberLevel;
        private Integer points;
        private Integer status;
        private LocalDateTime lastLoginTime;

        public boolean isActive() {
            return status != null && status == 1;
        }
    }
}
