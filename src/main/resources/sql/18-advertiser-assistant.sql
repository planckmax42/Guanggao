USE ad_platform;

-- 广告主副账号，用于授权其他人员协助运营。
CREATE TABLE IF NOT EXISTS advertiser_assistant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    public_id VARCHAR(40) NOT NULL,
    advertiser_id BIGINT NOT NULL COMMENT '广告主 ID，对应 advertiser.id',
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'OPERATOR',
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_advertiser_assistant_public_id (public_id),
    UNIQUE KEY uk_advertiser_assistant_username (username),
    KEY idx_advertiser_assistant_advertiser_status (advertiser_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO schema_version (version, description)
VALUES ('stage-18-advertiser-assistant', 'advertiser assistant account schema')
ON DUPLICATE KEY UPDATE description = VALUES(description);
