package cn.pandora.domain.user.event;

import cn.pandora.domain.event.DomainEvent;
import lombok.Getter;

/**
 * 用户创建领域事件
 */
@Getter
public class UserCreatedEvent extends DomainEvent {

    private final String username;
    private final String email;

    public UserCreatedEvent(String username, String email) {
        super();
        this.username = username;
        this.email = email;
    }
}
