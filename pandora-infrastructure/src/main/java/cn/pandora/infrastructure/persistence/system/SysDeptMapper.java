package cn.pandora.infrastructure.persistence.system;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysDeptMapper extends BaseMapper<SysDeptDO> {

    @Select("SELECT id FROM sys_dept WHERE ancestors LIKE CONCAT(#{ancestors}, ',', #{deptId}, '%') AND deleted = 0")
    List<Long> selectChildDeptIds(@Param("ancestors") String ancestors, @Param("deptId") Long deptId);

    @Select("SELECT id FROM sys_dept WHERE FIND_IN_SET(#{deptId}, ancestors) AND deleted = 0")
    List<Long> selectAllChildDeptIds(@Param("deptId") Long deptId);
}
