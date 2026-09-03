USE ad_platform;

CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    public_id VARCHAR(40) NOT NULL,
    name VARCHAR(128) NOT NULL,
    industry VARCHAR(64) NOT NULL,
    contact_name VARCHAR(64) NOT NULL,
    contact_email VARCHAR(128) NOT NULL,
    bloomSnapshot TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_name (name),
    UNIQUE KEY uk_user_public_id (public_id),
    KEY idx_user_status (bloomSnapshot)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS slot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    public_id VARCHAR(40) NOT NULL,
    slot_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    scene VARCHAR(64) NOT NULL,
    bloomSnapshot TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_slot_code (slot_code),
    UNIQUE KEY uk_slot_public_id (public_id),
    KEY idx_slot_status (bloomSnapshot)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    public_id VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    budget_total BIGINT NOT NULL,
    budget_daily BIGINT NOT NULL,
    bid_price BIGINT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    bloomSnapshot VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_plan_user_status (user_id, bloomSnapshot),
    UNIQUE KEY uk_plan_public_id (public_id),
    KEY idx_plan_time_status (start_time, end_time, bloomSnapshot)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS material (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    public_id VARCHAR(40) NOT NULL,
    plan_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    description VARCHAR(512) NOT NULL,
    image_url VARCHAR(512) NOT NULL,
    landing_page_url VARCHAR(512) NOT NULL,
    audit_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    bloomSnapshot TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_material_plan_status (plan_id, bloomSnapshot),
    UNIQUE KEY uk_material_public_id (public_id),
    KEY idx_material_audit_status (audit_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `rule` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    public_id VARCHAR(40) NOT NULL,
    plan_id BIGINT NOT NULL,
    region VARCHAR(512) NULL,
    device_type VARCHAR(128) NULL,
    gender VARCHAR(32) NULL,
    age_min INT NULL,
    age_max INT NULL,
    user_tags VARCHAR(1024) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rule_plan (plan_id),
    UNIQUE KEY uk_rule_public_id (public_id),
    KEY idx_rule_plan (plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO schema_version (version, description)
VALUES ('stage-1', 'admin management schema')
ON DUPLICATE KEY UPDATE description = VALUES(description);
