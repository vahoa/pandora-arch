package cn.pandora.domain.event;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 领域事件基类
 */
public abstract class DomainEvent implements Serializable {

    private final String eventId;
    private final LocalDateTime occurredOn;

    protected DomainEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.occurredOn = LocalDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }

    public LocalDateTime getOccurredOn() {
        return occurredOn;
    }

    /**
     * 获取事件类型名称，默认使用类名
     */
    public String getEventType() {
        return this.getClass().getSimpleName();
    }
}
