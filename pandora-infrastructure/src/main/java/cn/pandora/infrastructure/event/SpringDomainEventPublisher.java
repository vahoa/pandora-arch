package cn.pandora.infrastructure.event;

import cn.pandora.domain.event.DomainEvent;
import cn.pandora.domain.event.DomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring ApplicationEvent 的领域事件发布实现
 */
@Slf4j
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(DomainEvent event) {
        log.info("发布领域事件: type={}, id={}", event.getEventType(), event.getEventId());
        applicationEventPublisher.publishEvent(event);
    }
}
