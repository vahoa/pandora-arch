package cn.pandora.domain.model;

import cn.pandora.domain.event.DomainEvent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聚合根基类。
 * 聚合根是领域中一致性边界的入口，负责维护聚合内不变量。
 * 内部维护领域事件列表，在持久化后统一发布。
 */
public abstract class AggregateRoot<ID extends Serializable> extends Entity<ID> {

    private final transient List<DomainEvent> domainEvents = new ArrayList<>();

    protected void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }
}
