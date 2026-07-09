USE ad_platform;

SET @campaign_billing_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ad_campaign'
      AND COLUMN_NAME = 'billing_type'
);
SET @add_campaign_billing_column_sql := IF(
    @campaign_billing_column_exists = 0,
    'ALTER TABLE ad_campaign ADD COLUMN billing_type VARCHAR(16) NOT NULL DEFAULT ''CPC'' AFTER bid_price',
    'SELECT 1'
);
PREPARE add_campaign_billing_column_stmt FROM @add_campaign_billing_column_sql;
EXECUTE add_campaign_billing_column_stmt;
DEALLOCATE PREPARE add_campaign_billing_column_stmt;

CREATE TABLE IF NOT EXISTS ad_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NULL,
    event_type VARCHAR(32) NOT NULL,
    campaign_id BIGINT NOT NULL,
    creative_id BIGINT NOT NULL,
    ad_slot_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    billing_type VARCHAR(16) NOT NULL,
    charged TINYINT NOT NULL DEFAULT 0,
    cost_amount BIGINT NOT NULL DEFAULT 0,
    event_time DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ad_event_event_id (event_id),
    KEY idx_ad_event_campaign_time (campaign_id, event_time),
    KEY idx_ad_event_creative_time (creative_id, event_time),
    KEY idx_ad_event_user_type_time (user_id, event_type, event_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ad_stats_daily (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    stat_date DATE NOT NULL,
    campaign_id BIGINT NOT NULL,
    creative_id BIGINT NOT NULL,
    ad_slot_id BIGINT NOT NULL,
    impression_count BIGINT NOT NULL DEFAULT 0,
    click_count BIGINT NOT NULL DEFAULT 0,
    conversion_count BIGINT NOT NULL DEFAULT 0,
    cost_amount BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_stats_daily_creative (stat_date, campaign_id, creative_id),
    KEY idx_stats_daily_campaign (stat_date, campaign_id),
    KEY idx_stats_daily_slot (stat_date, ad_slot_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO schema_version (version, description)
VALUES ('stage-3-business-tracking-report', 'mysql based event tracking billing and report schema')
ON DUPLICATE KEY UPDATE description = VALUES(description);
