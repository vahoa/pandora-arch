package cn.pandora.infrastructure.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户 JPA 实体 —— 演示 JPA 与 MyBatis-Flex 多 ORM 共存
 */
@Data
@Entity
@Table(name = "sys_user")
public class UserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 200)
    private String password;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private Integer status;

    @Column(nullable = false)
    private Integer deleted;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}
