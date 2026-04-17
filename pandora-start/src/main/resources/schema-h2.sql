CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL,
    password    VARCHAR(200) NOT NULL,
    email       VARCHAR(100) NOT NULL,
    phone       VARCHAR(20)  DEFAULT NULL,
    status      TINYINT      NOT NULL DEFAULT 1,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_username UNIQUE (username),
    CONSTRAINT uk_email UNIQUE (email)
);

INSERT INTO sys_user (username, password, email, phone, status) VALUES
    ('admin', 'admin123', 'admin@example.com', '13800000001', 1),
    ('testuser', 'test123', 'test@example.com', '13800000002', 1);
