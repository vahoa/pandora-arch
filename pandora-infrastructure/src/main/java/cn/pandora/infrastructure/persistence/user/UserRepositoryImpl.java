package cn.pandora.infrastructure.persistence.user;

import cn.pandora.domain.user.User;
import cn.pandora.domain.user.UserRepository;
import cn.pandora.infrastructure.persistence.converter.UserConverter;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户仓储实现（基础设施层）—— MyBatis-Flex
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;

    public UserRepositoryImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public User save(User user) {
        UserDO dataObject = UserConverter.toDataObject(user);
        if (dataObject.getId() == null) {
            userMapper.insert(dataObject);
            user.assignId(dataObject.getId());
        } else {
            userMapper.update(dataObject);
        }
        return user;
    }

    @Override
    public Optional<User> findById(Long id) {
        UserDO dataObject = userMapper.selectOneById(id);
        return Optional.ofNullable(UserConverter.toDomainModel(dataObject));
    }

    @Override
    public void deleteById(Long id) {
        userMapper.deleteById(id);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        UserDO dataObject = userMapper.selectOneByQuery(
                QueryWrapper.create().eq("username", username));
        return Optional.ofNullable(UserConverter.toDomainModel(dataObject));
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.selectCountByQuery(
                QueryWrapper.create().eq("username", username)) > 0;
    }

    @Override
    public boolean existsByEmail(String email) {
        return userMapper.selectCountByQuery(
                QueryWrapper.create().eq("email", email)) > 0;
    }
}
