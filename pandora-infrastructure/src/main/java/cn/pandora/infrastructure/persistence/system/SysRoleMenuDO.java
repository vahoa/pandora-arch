package cn.pandora.infrastructure.persistence.system;

import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_role_menu")
public class SysRoleMenuDO implements Serializable {

    private Long roleId;
    private Long menuId;
}
