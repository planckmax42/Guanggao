USE ad_platform;

-- 存量环境先增加可空列并逐行回填，再收紧为 NOT NULL，避免迁移中断。
-- 使用 information_schema + 动态 DDL，兼容不支持 ADD COLUMN IF NOT EXISTS 的 MySQL 版本。
SET @column_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'public_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `user` ADD COLUMN public_id VARCHAR(40) NULL AFTER id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slot' AND COLUMN_NAME = 'public_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE slot ADD COLUMN public_id VARCHAR(40) NULL AFTER id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plan' AND COLUMN_NAME = 'public_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE plan ADD COLUMN public_id VARCHAR(40) NULL AFTER id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material' AND COLUMN_NAME = 'public_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE material ADD COLUMN public_id VARCHAR(40) NULL AFTER id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule' AND COLUMN_NAME = 'public_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `rule` ADD COLUMN public_id VARCHAR(40) NULL AFTER id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- RANDOM_BYTES 避免 MySQL UUID() 的时间型 UUID 透出时间和节点信息。
UPDATE `user` SET public_id = CONCAT('adv_', LOWER(HEX(RANDOM_BYTES(16)))) WHERE public_id IS NULL;
UPDATE slot SET public_id = CONCAT('slot_', LOWER(HEX(RANDOM_BYTES(16)))) WHERE public_id IS NULL;
UPDATE plan SET public_id = CONCAT('plan_', LOWER(HEX(RANDOM_BYTES(16)))) WHERE public_id IS NULL;
UPDATE material SET public_id = CONCAT('mat_', LOWER(HEX(RANDOM_BYTES(16)))) WHERE public_id IS NULL;
UPDATE `rule` SET public_id = CONCAT('rule_', LOWER(HEX(RANDOM_BYTES(16)))) WHERE public_id IS NULL;

ALTER TABLE `user` MODIFY public_id VARCHAR(40) NOT NULL;
ALTER TABLE slot MODIFY public_id VARCHAR(40) NOT NULL;
ALTER TABLE plan MODIFY public_id VARCHAR(40) NOT NULL;
ALTER TABLE material MODIFY public_id VARCHAR(40) NOT NULL;
ALTER TABLE `rule` MODIFY public_id VARCHAR(40) NOT NULL;

SET @index_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'uk_user_public_id'
);
SET @sql := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_user_public_id ON `user` (public_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slot' AND INDEX_NAME = 'uk_slot_public_id'
);
SET @sql := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_slot_public_id ON slot (public_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plan' AND INDEX_NAME = 'uk_plan_public_id'
);
SET @sql := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_plan_public_id ON plan (public_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material' AND INDEX_NAME = 'uk_material_public_id'
);
SET @sql := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_material_public_id ON material (public_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule' AND INDEX_NAME = 'uk_rule_public_id'
);
SET @sql := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_rule_public_id ON `rule` (public_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO schema_version (version, description)
VALUES ('stage-9-public-identifiers', 'immutable public identifiers for externally addressable resources')
ON DUPLICATE KEY UPDATE description = VALUES(description);
