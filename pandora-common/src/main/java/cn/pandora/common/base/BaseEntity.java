package cn.pandora.common.base;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据库实体基类（DO 层面），提供通用审计字段
 */
@Data
public abstract class BaseEntity implements Serializable {

    private Long id;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
