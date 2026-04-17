package cn.pandora.infrastructure.persistence.system;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysMenuMapper extends BaseMapper<SysMenuDO> {

    @Select("SELECT DISTINCT m.* FROM sys_menu m " +
            "INNER JOIN sys_role_menu rm ON m.id = rm.menu_id " +
            "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND m.status = 1 AND m.deleted = 0 " +
            "ORDER BY m.sort")
    List<SysMenuDO> selectMenusByUserId(@Param("userId") Long userId);

    @Select("SELECT DISTINCT m.permission FROM sys_menu m " +
            "INNER JOIN sys_role_menu rm ON m.id = rm.menu_id " +
            "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND m.menu_type = 3 AND m.status = 1 AND m.deleted = 0 " +
            "AND m.permission IS NOT NULL AND m.permission != ''")
    List<String> selectPermissionsByUserId(@Param("userId") Long userId);
}
