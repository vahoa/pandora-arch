-- DDD架构底座 数据库初始化脚本

CREATE DATABASE IF NOT EXISTS `ddd_platform` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `ddd_platform`;

-- ==================== 用户表 ====================
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username`    VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(200) NOT NULL COMMENT '密码',
    `email`       VARCHAR(100) NOT NULL COMMENT '邮箱',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 插入演示数据
INSERT INTO `sys_user` (`username`, `password`, `email`, `phone`, `status`)
VALUES
    ('admin', 'admin123', 'admin@example.com', '13800000001', 1),
    ('testuser', 'test123', 'test@example.com', '13800000002', 1);
