USE ad_platform;

-- 仅在旧名称存在时执行改名，重复执行时跳过已完成的操作。
SET @sql := IF(EXISTS (
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user'
)
AND NOT EXISTS (
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'advertiser'
), 'RENAME TABLE `user` TO advertiser', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plan' AND COLUMN_NAME = 'user_id'
), 'ALTER TABLE plan RENAME COLUMN user_id TO advertiser_id', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'advertiser' AND INDEX_NAME = 'uk_user_name'
), 'ALTER TABLE advertiser RENAME INDEX uk_user_name TO uk_advertiser_name', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'advertiser' AND INDEX_NAME = 'uk_user_public_id'
), 'ALTER TABLE advertiser RENAME INDEX uk_user_public_id TO uk_advertiser_public_id', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'advertiser' AND INDEX_NAME = 'idx_user_status'
), 'ALTER TABLE advertiser RENAME INDEX idx_user_status TO idx_advertiser_status', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plan' AND INDEX_NAME = 'idx_plan_user_status'
), 'ALTER TABLE plan RENAME INDEX idx_plan_user_status TO idx_plan_advertiser_status', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO schema_version (version, description)
VALUES ('stage-11-rename-user-to-advertiser', 'rename advertiser table and related identifiers')
ON DUPLICATE KEY UPDATE description = VALUES(description);
