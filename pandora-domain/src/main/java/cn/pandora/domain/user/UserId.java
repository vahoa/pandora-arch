package cn.pandora.domain.user;

import cn.pandora.domain.model.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 用户ID值对象
 */
@Getter
@EqualsAndHashCode
public class UserId implements ValueObject {

    private final Long value;

    public UserId(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        this.value = value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
