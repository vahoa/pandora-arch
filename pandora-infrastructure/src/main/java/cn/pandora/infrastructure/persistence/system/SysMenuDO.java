package cn.pandora.infrastructure.persistence.system;

import com.mybatisflex.annotation.Table;
import cn.pandora.infrastructure.base.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("sys_menu")
public class SysMenuDO extends BaseDO {

    private Long parentId;
    private String menuName;
    private Integer menuType;
    private String path;
    private String component;
    private String icon;
    private String permission;
    private Integer sort;
    private Integer visible;
    private Integer status;
}
