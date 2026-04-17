package cn.pandora.infrastructure.persistence.system;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface SysUserRoleMapper extends BaseMapper<SysUserRoleDO> {

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
