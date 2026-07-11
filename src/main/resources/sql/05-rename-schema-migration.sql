USE ad_platform;

-- 无损命名迁移脚本：
-- 1. 不清空数据，不重建表。
-- 2. 旧表/旧字段存在且新表/新字段不存在时才执行改名，便于本地重复执行。
-- 3. 执行前建议先停止 Spring Boot 服务并备份数据库。

SET FOREIGN_KEY_CHECKS = 0;

-- 表名迁移。
SET @old_table_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'advertiser'
);
SET @new_table_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user'
);
SET @sql := IF(@old_table_exists = 1 AND @new_table_exists = 0,
    'RENAME TABLE advertiser TO `user`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_table_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ad_slot'
);
SET @new_table_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slot'
);
SET @sql := IF(@old_table_exists = 1 AND @new_table_exists = 0,
    'RENAME TABLE ad_slot TO slot',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_table_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ad_campaign'
);
SET @new_table_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plan'
);
SET @sql := IF(@old_table_exists = 1 AND @new_table_exists = 0,
    'RENAME TABLE ad_campaign TO plan',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_table_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ad_creative'
);
SET @new_table_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material'
);
SET @sql := IF(@old_table_exists = 1 AND @new_table_exists = 0,
    'RENAME TABLE ad_creative TO material',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_table_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ad_targeting_rule'
);
SET @new_table_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule'
);
SET @sql := IF(@old_table_exists = 1 AND @new_table_exists = 0,
    'RENAME TABLE ad_targeting_rule TO `rule`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_table_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ad_event'
);
SET @new_table_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event'
);
SET @sql := IF(@old_table_exists = 1 AND @new_table_exists = 0,
    'RENAME TABLE ad_event TO `event`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_table_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ad_stats_daily'
);
SET @new_table_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_report'
);
SET @sql := IF(@old_table_exists = 1 AND @new_table_exists = 0,
    'RENAME TABLE ad_stats_daily TO daily_report',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 字段名迁移。
SET @old_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plan' AND COLUMN_NAME = 'advertiser_id'
);
SET @new_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plan' AND COLUMN_NAME = 'user_id'
);
SET @sql := IF(@old_column_exists = 1 AND @new_column_exists = 0,
    'ALTER TABLE plan RENAME COLUMN advertiser_id TO user_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material' AND COLUMN_NAME = 'campaign_id'
);
SET @new_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material' AND COLUMN_NAME = 'plan_id'
);
SET @sql := IF(@old_column_exists = 1 AND @new_column_exists = 0,
    'ALTER TABLE material RENAME COLUMN campaign_id TO plan_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material' AND COLUMN_NAME = 'ad_slot_id'
);
SET @new_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material' AND COLUMN_NAME = 'slot_id'
);
SET @sql := IF(@old_column_exists = 1 AND @new_column_exists = 0,
    'ALTER TABLE material RENAME COLUMN ad_slot_id TO slot_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule' AND COLUMN_NAME = 'campaign_id'
);
SET @new_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule' AND COLUMN_NAME = 'plan_id'
);
SET @sql := IF(@old_column_exists = 1 AND @new_column_exists = 0,
    'ALTER TABLE `rule` RENAME COLUMN campaign_id TO plan_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event' AND COLUMN_NAME = 'campaign_id'
);
SET @new_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event' AND COLUMN_NAME = 'plan_id'
);
SET @sql := IF(@old_column_exists = 1 AND @new_column_exists = 0,
    'ALTER TABLE `event` RENAME COLUMN campaign_id TO plan_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event' AND COLUMN_NAME = 'creative_id'
);
SET @new_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event' AND COLUMN_NAME = 'material_id'
);
SET @sql := IF(@old_column_exists = 1 AND @new_column_exists = 0,
    'ALTER TABLE `event` RENAME COLUMN creative_id TO material_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event' AND COLUMN_NAME = 'ad_slot_id'
);
SET @new_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event' AND COLUMN_NAME = 'slot_id'
);
SET @sql := IF(@old_column_exists = 1 AND @new_column_exists = 0,
    'ALTER TABLE `event` RENAME COLUMN ad_slot_id TO slot_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event' AND COLUMN_NAME = 'user_id'
);
SET @new_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event' AND COLUMN_NAME = 'viewer_id'
);
SET @sql := IF(@old_column_exists = 1 AND @new_column_exists = 0,
    'ALTER TABLE `event` RENAME COLUMN user_id TO viewer_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_report' AND COLUMN_NAME = 'campaign_id'
);
SET @new_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_report' AND COLUMN_NAME = 'plan_id'
);
SET @sql := IF(@old_column_exists = 1 AND @new_column_exists = 0,
    'ALTER TABLE daily_report RENAME COLUMN campaign_id TO plan_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_report' AND COLUMN_NAME = 'creative_id'
);
SET @new_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_report' AND COLUMN_NAME = 'material_id'
);
SET @sql := IF(@old_column_exists = 1 AND @new_column_exists = 0,
    'ALTER TABLE daily_report RENAME COLUMN creative_id TO material_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_report' AND COLUMN_NAME = 'ad_slot_id'
);
SET @new_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_report' AND COLUMN_NAME = 'slot_id'
);
SET @sql := IF(@old_column_exists = 1 AND @new_column_exists = 0,
    'ALTER TABLE daily_report RENAME COLUMN ad_slot_id TO slot_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 索引名迁移。索引名本身不影响数据，但统一后更方便排查 SQL。
SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'uk_advertiser_name'
);
SET @sql := IF(@index_exists > 0, 'ALTER TABLE `user` DROP INDEX uk_advertiser_name', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'uk_user_name'
);
SET @sql := IF(@index_exists = 0, 'ALTER TABLE `user` ADD UNIQUE KEY uk_user_name (name)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slot' AND INDEX_NAME = 'uk_ad_slot_code'
);
SET @sql := IF(@index_exists > 0, 'ALTER TABLE slot DROP INDEX uk_ad_slot_code', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slot' AND INDEX_NAME = 'uk_slot_code'
);
SET @sql := IF(@index_exists = 0, 'ALTER TABLE slot ADD UNIQUE KEY uk_slot_code (slot_code)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plan' AND INDEX_NAME = 'idx_campaign_advertiser_status'
);
SET @sql := IF(@index_exists > 0, 'ALTER TABLE plan DROP INDEX idx_campaign_advertiser_status', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plan' AND INDEX_NAME = 'idx_plan_user_status'
);
SET @sql := IF(@index_exists = 0, 'ALTER TABLE plan ADD INDEX idx_plan_user_status (user_id, status)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material' AND INDEX_NAME = 'idx_creative_campaign_status'
);
SET @sql := IF(@index_exists > 0, 'ALTER TABLE material DROP INDEX idx_creative_campaign_status', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material' AND INDEX_NAME = 'idx_material_plan_status'
);
SET @sql := IF(@index_exists = 0, 'ALTER TABLE material ADD INDEX idx_material_plan_status (plan_id, status)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event' AND INDEX_NAME = 'uk_ad_event_event_id'
);
SET @sql := IF(@index_exists > 0, 'ALTER TABLE `event` DROP INDEX uk_ad_event_event_id', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event' AND INDEX_NAME = 'uk_event_event_id'
);
SET @sql := IF(@index_exists = 0, 'ALTER TABLE `event` ADD UNIQUE KEY uk_event_event_id (event_id)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event' AND INDEX_NAME = 'idx_ad_event_user_type_time'
);
SET @sql := IF(@index_exists > 0, 'ALTER TABLE `event` DROP INDEX idx_ad_event_user_type_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event' AND INDEX_NAME = 'idx_event_viewer_type_time'
);
SET @sql := IF(@index_exists = 0, 'ALTER TABLE `event` ADD INDEX idx_event_viewer_type_time (viewer_id, event_type, event_time)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_report' AND INDEX_NAME = 'uk_stats_daily_creative'
);
SET @sql := IF(@index_exists > 0, 'ALTER TABLE daily_report DROP INDEX uk_stats_daily_creative', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_report' AND INDEX_NAME = 'uk_daily_report_material'
);
SET @sql := IF(@index_exists = 0, 'ALTER TABLE daily_report ADD UNIQUE KEY uk_daily_report_material (stat_date, plan_id, material_id)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO schema_version (version, description)
VALUES ('stage-5-rename-schema', 'lossless rename migration for simplified table and column names')
ON DUPLICATE KEY UPDATE description = VALUES(description);
