package cn.pandora.domain.user;

import cn.pandora.common.exception.BusinessException;
import cn.pandora.common.result.ResultCode;
import cn.pandora.domain.model.AggregateRoot;
import cn.pandora.domain.user.event.UserCreatedEvent;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 用户聚合根
 */
@Getter
public class User extends AggregateRoot<Long> {

    private Long id;
    private String username;
    private String password;
    private String email;
    private String phone;
    private UserStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    protected User() {
    }

    /**
     * 创建用户（工厂方法）
     */
    public static User create(String username, String password, String email, String phone) {
        User user = new User();
        user.username = username;
        user.password = password;
        user.email = email;
        user.phone = phone;
        user.status = UserStatus.ACTIVE;
        user.createTime = LocalDateTime.now();
        user.updateTime = LocalDateTime.now();

        user.registerEvent(new UserCreatedEvent(user.username, user.email));
        return user;
    }

    /**
     * 从持久化数据重建聚合根
     */
    public static User reconstruct(Long id, String username, String password,
                                    String email, String phone, UserStatus status,
                                    LocalDateTime createTime, LocalDateTime updateTime) {
        User user = new User();
        user.id = id;
        user.username = username;
        user.password = password;
        user.email = email;
        user.phone = phone;
        user.status = status;
        user.createTime = createTime;
        user.updateTime = updateTime;
        return user;
    }

    public void changePassword(String oldPassword, String newPassword) {
        if (!this.password.equals(oldPassword)) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }
        this.password = newPassword;
        this.updateTime = LocalDateTime.now();
    }

    public void disable() {
        this.status = UserStatus.DISABLED;
        this.updateTime = LocalDateTime.now();
    }

    public void enable() {
        this.status = UserStatus.ACTIVE;
        this.updateTime = LocalDateTime.now();
    }

    public void assignId(Long id) {
        this.id = id;
    }

    @Override
    public Long getId() {
        return this.id;
    }
}
