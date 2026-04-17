package cn.pandora.infrastructure.persistence.system;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysRoleDeptMapper extends BaseMapper<SysRoleDeptDO> {

    @Delete("DELETE FROM sys_role_dept WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    @Select("SELECT dept_id FROM sys_role_dept WHERE role_id = #{roleId}")
    List<Long> selectDeptIdsByRoleId(@Param("roleId") Long roleId);
}
