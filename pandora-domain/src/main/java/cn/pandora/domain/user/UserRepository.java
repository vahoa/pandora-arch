package cn.pandora.domain.user;

import cn.pandora.domain.repository.Repository;

import java.util.Optional;

/**
 * 用户仓储接口（领域层定义，基础设施层实现）
 */
public interface UserRepository extends Repository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
