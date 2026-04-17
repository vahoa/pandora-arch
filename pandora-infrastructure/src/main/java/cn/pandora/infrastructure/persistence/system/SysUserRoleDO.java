package cn.pandora.infrastructure.persistence.system;

import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_user_role")
public class SysUserRoleDO implements Serializable {

    private Long userId;
    private Long roleId;
}
