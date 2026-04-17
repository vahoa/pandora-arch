package cn.pandora.domain.event;

import java.util.List;

/**
 * 领域事件发布接口，具体实现由基础设施层提供
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);

    default void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
    }
}
