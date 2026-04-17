package cn.pandora.infrastructure.persistence.system;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysRoleMapper extends BaseMapper<SysRoleDO> {

    @Select("SELECT r.* FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.deleted = 0 AND r.status = 1")
    List<SysRoleDO> selectRolesByUserId(@Param("userId") Long userId);
}
