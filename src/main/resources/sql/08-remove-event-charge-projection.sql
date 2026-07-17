USE ad_platform;

-- 扣费结果只由 charge_record 维护，event 仅保留原始事件快照。
SET @event_charged_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'event'
      AND COLUMN_NAME = 'charged'
);
SET @drop_event_charged_column_sql := IF(
    @event_charged_column_exists > 0,
    'ALTER TABLE `event` DROP COLUMN charged',
    'SELECT 1'
);
PREPARE drop_event_charged_column_stmt FROM @drop_event_charged_column_sql;
EXECUTE drop_event_charged_column_stmt;
DEALLOCATE PREPARE drop_event_charged_column_stmt;

SET @event_cost_amount_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'event'
      AND COLUMN_NAME = 'cost_amount'
);
SET @drop_event_cost_amount_column_sql := IF(
    @event_cost_amount_column_exists > 0,
    'ALTER TABLE `event` DROP COLUMN cost_amount',
    'SELECT 1'
);
PREPARE drop_event_cost_amount_column_stmt FROM @drop_event_cost_amount_column_sql;
EXECUTE drop_event_cost_amount_column_stmt;
DEALLOCATE PREPARE drop_event_cost_amount_column_stmt;

INSERT INTO schema_version (version, description)
VALUES ('remove-event-charge-projection', 'charge state is owned by charge_record instead of event')
ON DUPLICATE KEY UPDATE description = VALUES(description);
