package cn.pandora.auth.init;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 密码迁移组件 —— 启动时自动检测并将明文密码加密为 BCrypt
 * <p>
 * BCrypt 密文固定以 $2a$、$2b$ 或 $2y$ 开头，
 * 不符合此格式的密码视为明文，自动加密后回写数据库。
 */
@Slf4j
@Component
public class PasswordMigrationRunner implements CommandLineRunner {

    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{1,2}\\$.{53}$");
    /** 已带 {id} 前缀的委派编码（如 {bcrypt}、{noop}、{pbkdf2} 等）视为已加密，不再迁移。 */
    private static final Pattern DELEGATING_PREFIX_PATTERN = Pattern.compile("^\\{[a-zA-Z0-9_-]+\\}.+");

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public PasswordMigrationRunner(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT id, username, password FROM sys_user WHERE deleted = 0 AND password IS NOT NULL AND password != ''");

        log.info("扫描到 {} 个用户待检查密码格式", users.size());

        int migrated = 0;
        for (Map<String, Object> user : users) {
            Object idObj = user.get("id");
            String username = (String) user.get("username");
            String password = (String) user.get("password");

            if (!BCRYPT_PATTERN.matcher(password).matches()
                    && !DELEGATING_PREFIX_PATTERN.matcher(password).matches()) {
                String encoded = passwordEncoder.encode(password);
                int rows = jdbcTemplate.update("UPDATE sys_user SET password = ? WHERE id = ?",
                        encoded, idObj);
                migrated++;
                log.info("已迁移用户 [{}](id={}) 的密码为 BCrypt 密文, 影响行数: {}", username, idObj, rows);
            }
        }

        if (migrated > 0) {
            log.info("密码迁移完成，共迁移 {} 个用户", migrated);
            List<Map<String, Object>> verify = jdbcTemplate.queryForList(
                    "SELECT id, username, LEFT(password, 10) as pwd_prefix, LENGTH(password) as pwd_len FROM sys_user WHERE deleted = 0");
            verify.forEach(v -> log.info("验证 -> 用户: {}, 密码前缀: {}, 密码长度: {}", v.get("username"), v.get("pwd_prefix"), v.get("pwd_len")));
        } else {
            log.info("所有用户密码已为 BCrypt 密文，无需迁移");
        }
    }
}
