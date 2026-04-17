package cn.pandora.infrastructure.persistence.system;

import com.mybatisflex.annotation.Table;
import cn.pandora.infrastructure.base.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("sys_role")
public class SysRoleDO extends BaseDO {

    private String roleName;
    private String roleCode;
    private Integer sort;
    private Integer dataScope;
    private Integer status;
    private String remark;
}
