package cn.pandora.infrastructure.persistence.jpa.repository;

import cn.pandora.infrastructure.persistence.jpa.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户 JPA 仓储 —— 演示 JPA 作为替代 ORM 使用
 * <p>
 * 与 MyBatis-Flex 的 UserMapper 操作同一张表，展示多 ORM 共存能力。
 * 实际项目中可根据场景选择：
 * - MyBatis-Flex：适合复杂 SQL、动态查询
 * - JPA：适合简单 CRUD、领域模型映射
 */
@Repository
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long>,
        JpaSpecificationExecutor<UserJpaEntity> {

    Optional<UserJpaEntity> findByUsername(String username);

    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
