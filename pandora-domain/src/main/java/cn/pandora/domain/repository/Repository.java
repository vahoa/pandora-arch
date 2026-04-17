package cn.pandora.domain.repository;

import java.io.Serializable;
import java.util.Optional;

/**
 * 仓储泛型接口，面向聚合根
 */
public interface Repository<T, ID extends Serializable> {

    T save(T aggregate);

    Optional<T> findById(ID id);

    void deleteById(ID id);
}
