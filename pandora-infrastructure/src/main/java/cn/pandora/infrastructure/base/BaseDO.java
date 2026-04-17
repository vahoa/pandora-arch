package cn.pandora.infrastructure.base;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据对象基类（MyBatis-Flex）
 * 审计字段 + 逻辑删除 + 操作人（通过 Listener 自动填充）
 */
@Data
public abstract class BaseDO implements Serializable {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long createBy;

    private Long updateBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Integer deleted;
}
