package cn.pandora.infrastructure.config;

import cn.pandora.common.security.LoginUser;
import cn.pandora.common.security.LoginUserHolder;
import cn.pandora.infrastructure.base.BaseDO;
import cn.pandora.infrastructure.security.datascope.DataPermissionInterceptor;
import com.mybatisflex.annotation.InsertListener;
import com.mybatisflex.annotation.UpdateListener;
import com.mybatisflex.core.FlexGlobalConfig;
import com.mybatisflex.core.audit.AuditManager;
import com.mybatisflex.spring.boot.MyBatisFlexCustomizer;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * MyBatis-Flex 全局配置 —— 自动填充 + 审计日志
 * <p>
 * 通过 Listener 实现 BaseDO 审计字段自动注入，
 * 等价于 MyBatis-Flex 的 MetaObjectHandler。
 */
@Configuration
/*
 * 仅扫描 MyBatis-Flex Mapper 所在子包，避免误扫 jpa.repository 导致与
 * Spring Data JPA 的 JpaRepositoryFactoryBean 产生同名 Bean 冲突。
 */
@MapperScan(basePackages = {
        "cn.pandora.infrastructure.persistence.system",
        "cn.pandora.infrastructure.persistence.user"
})
public class MybatisFlexConfig implements MyBatisFlexCustomizer {

    @Override
    public void customize(FlexGlobalConfig globalConfig) {
        globalConfig.registerInsertListener(new AuditInsertListener(), BaseDO.class);
        globalConfig.registerUpdateListener(new AuditUpdateListener(), BaseDO.class);

        AuditManager.setAuditEnable(false);
    }

    @Bean
    public DataPermissionInterceptor dataPermissionInterceptor() {
        return new DataPermissionInterceptor();
    }

    static class AuditInsertListener implements InsertListener {
        @Override
        public void onInsert(Object entity) {
            if (entity instanceof BaseDO bo) {
                LocalDateTime now = LocalDateTime.now();
                if (bo.getCreateTime() == null) bo.setCreateTime(now);
                if (bo.getUpdateTime() == null) bo.setUpdateTime(now);
                if (bo.getDeleted() == null) bo.setDeleted(0);
                currentUserId().ifPresent(uid -> {
                    if (bo.getCreateBy() == null) bo.setCreateBy(uid);
                    if (bo.getUpdateBy() == null) bo.setUpdateBy(uid);
                });
            }
        }
    }

    static class AuditUpdateListener implements UpdateListener {
        @Override
        public void onUpdate(Object entity) {
            if (entity instanceof BaseDO bo) {
                bo.setUpdateTime(LocalDateTime.now());
                currentUserId().ifPresent(bo::setUpdateBy);
            }
        }
    }

    private static Optional<Long> currentUserId() {
        LoginUser user = LoginUserHolder.get();
        return Optional.ofNullable(user).map(LoginUser::getUserId);
    }
}
