package cn.pandora.domain.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * 实体基类，通过唯一标识判断相等性
 */
public abstract class Entity<ID extends Serializable> implements Serializable {

    public abstract ID getId();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity<?> entity = (Entity<?>) o;
        return Objects.equals(getId(), entity.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
