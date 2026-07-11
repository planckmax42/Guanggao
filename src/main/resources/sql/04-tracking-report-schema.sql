USE ad_platform;

SET @plan_billing_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'plan'
      AND COLUMN_NAME = 'billing_type'
);
SET @add_plan_billing_column_sql := IF(
    @plan_billing_column_exists = 0,
    'ALTER TABLE plan ADD COLUMN billing_type VARCHAR(16) NOT NULL DEFAULT ''CPC'' AFTER bid_price',
    'SELECT 1'
);
PREPARE add_plan_billing_column_stmt FROM @add_plan_billing_column_sql;
EXECUTE add_plan_billing_column_stmt;
DEALLOCATE PREPARE add_plan_billing_column_stmt;

CREATE TABLE IF NOT EXISTS `event` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NULL,
    event_type VARCHAR(32) NOT NULL,
    plan_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    viewer_id BIGINT NOT NULL,
    billing_type VARCHAR(16) NOT NULL,
    charged TINYINT NOT NULL DEFAULT 0,
    cost_amount BIGINT NOT NULL DEFAULT 0,
    event_time DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_event_event_id (event_id),
    KEY idx_event_plan_time (plan_id, event_time),
    KEY idx_event_material_time (material_id, event_time),
    KEY idx_event_viewer_type_time (viewer_id, event_type, event_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS daily_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    stat_date DATE NOT NULL,
    plan_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    impression_count BIGINT NOT NULL DEFAULT 0,
    click_count BIGINT NOT NULL DEFAULT 0,
    conversion_count BIGINT NOT NULL DEFAULT 0,
    cost_amount BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_daily_report_material (stat_date, plan_id, material_id),
    KEY idx_daily_report_plan (stat_date, plan_id),
    KEY idx_daily_report_slot (stat_date, slot_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO schema_version (version, description)
VALUES ('stage-3-business-tracking-report', 'mysql based event tracking billing and report schema')
ON DUPLICATE KEY UPDATE description = VALUES(description);
