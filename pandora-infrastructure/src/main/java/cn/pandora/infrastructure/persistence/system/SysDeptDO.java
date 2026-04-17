package cn.pandora.infrastructure.persistence.system;

import com.mybatisflex.annotation.Table;
import cn.pandora.infrastructure.base.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("sys_dept")
public class SysDeptDO extends BaseDO {

    private Long parentId;
    private String ancestors;
    private String deptName;
    private Integer sort;
    private String leader;
    private String phone;
    private Integer status;
}
