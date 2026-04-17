package cn.pandora.infrastructure.event.handler;

import cn.pandora.domain.user.event.UserCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 用户领域事件处理器
 */
@Slf4j
@Component
public class UserEventHandler {

    @Async
    @EventListener
    public void handleUserCreated(UserCreatedEvent event) {
        log.info("处理用户创建事件: username={}, email={}, eventId={}",
                event.getUsername(), event.getEmail(), event.getEventId());
        // 此处可扩展：发送欢迎邮件、初始化用户配置、同步到其他系统等
    }
}
