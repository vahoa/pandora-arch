package cn.pandora.infrastructure.persistence.user;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 MyBatis-Flex Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
