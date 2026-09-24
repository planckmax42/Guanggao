USE ad_platform;

-- 存量环境先执行 11-rename-user-to-advertiser.sql，再创建新的用户表。
CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL COMMENT '姓名',
    gender VARCHAR(16) NULL COMMENT '性别',
    birth_date DATE NULL COMMENT '出生日期',
    address VARCHAR(255) NULL COMMENT '地址',
    phone VARCHAR(32) NULL COMMENT '电话'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 用户标签，每行存储一个标签。
CREATE TABLE IF NOT EXISTS user_tags (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tags VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_user_tags_tags (tags)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 用户与标签的多对多关联。
CREATE TABLE IF NOT EXISTS user_tags_relations (
    user_id BIGINT NOT NULL COMMENT '用户 ID，对应 user.id',
    tag_id BIGINT NOT NULL COMMENT '标签 ID，对应 user_tags.id',
    PRIMARY KEY (user_id, tag_id),
    KEY idx_user_tags_relations_tag_id (tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO schema_version (version, description)
VALUES ('stage-11-user-schema', 'user profile and user tags schema')
ON DUPLICATE KEY UPDATE description = VALUES(description);
