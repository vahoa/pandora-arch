package cn.pandora.infrastructure.persistence.system;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenuDO> {

    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
