USE ad_platform;

-- 将素材所属计划迁移到关联表。
SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material' AND COLUMN_NAME = 'plan_id'
);
SET @sql := IF(@column_exists > 0,
    'INSERT IGNORE INTO plan_material_relations (plan_id, material_id) SELECT plan_id, id FROM material',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 将规则所属计划迁移到关联表。
SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule' AND COLUMN_NAME = 'plan_id'
);
SET @sql := IF(@column_exists > 0,
    'INSERT IGNORE INTO plan_rule_relations (plan_id, rule_id) SELECT plan_id, id FROM `rule`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 删除依赖 material.plan_id 的旧索引。
SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material' AND INDEX_NAME = 'idx_material_plan_status'
);
SET @sql := IF(@index_exists > 0,
    'ALTER TABLE material DROP INDEX idx_material_plan_status',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 删除依赖 rule.plan_id 的旧索引。
SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule' AND INDEX_NAME = 'uk_rule_plan'
);
SET @sql := IF(@index_exists > 0,
    'ALTER TABLE `rule` DROP INDEX uk_rule_plan',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule' AND INDEX_NAME = 'idx_rule_plan'
);
SET @sql := IF(@index_exists > 0,
    'ALTER TABLE `rule` DROP INDEX idx_rule_plan',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 删除已经迁移的字段。
SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material' AND COLUMN_NAME = 'plan_id'
);
SET @sql := IF(@column_exists > 0,
    'ALTER TABLE material DROP COLUMN plan_id',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule' AND COLUMN_NAME = 'plan_id'
);
SET @sql := IF(@column_exists > 0,
    'ALTER TABLE `rule` DROP COLUMN plan_id',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule' AND COLUMN_NAME = 'user_tags'
);
SET @sql := IF(@column_exists > 0,
    'ALTER TABLE `rule` DROP COLUMN user_tags',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO schema_version (version, description)
VALUES ('stage-14-move-plan-relations', 'move plan relations and remove rule user tags')
ON DUPLICATE KEY UPDATE description = VALUES(description);
