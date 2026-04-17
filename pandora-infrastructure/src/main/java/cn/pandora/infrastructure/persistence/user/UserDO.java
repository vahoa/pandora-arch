package cn.pandora.infrastructure.persistence.user;

import com.mybatisflex.annotation.Table;
import cn.pandora.infrastructure.base.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户数据对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("sys_user")
public class UserDO extends BaseDO {

    private String username;
    private String password;
    private String email;
    private String phone;
    private Long deptId;
    private String nickname;
    private String avatar;
    private Integer status;
}
