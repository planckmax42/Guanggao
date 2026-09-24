USE ad_platform;

-- 为缺少审计时间的表补充 created_at、updated_at。
SET @created_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schema_version' AND COLUMN_NAME = 'created_at'
);
SET @updated_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schema_version' AND COLUMN_NAME = 'updated_at'
);
SET @sql := CASE
    WHEN @created_at_exists = 0 AND @updated_at_exists = 0 THEN
        'ALTER TABLE schema_version ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    WHEN @created_at_exists = 0 THEN
        'ALTER TABLE schema_version ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP'
    WHEN @updated_at_exists = 0 THEN
        'ALTER TABLE schema_version ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    ELSE 'SELECT 1'
END;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @created_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material_tags' AND COLUMN_NAME = 'created_at'
);
SET @updated_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material_tags' AND COLUMN_NAME = 'updated_at'
);
SET @sql := CASE
    WHEN @created_at_exists = 0 AND @updated_at_exists = 0 THEN
        'ALTER TABLE material_tags ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    WHEN @created_at_exists = 0 THEN
        'ALTER TABLE material_tags ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP'
    WHEN @updated_at_exists = 0 THEN
        'ALTER TABLE material_tags ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    ELSE 'SELECT 1'
END;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @created_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material_tags_relations' AND COLUMN_NAME = 'created_at'
);
SET @updated_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material_tags_relations' AND COLUMN_NAME = 'updated_at'
);
SET @sql := CASE
    WHEN @created_at_exists = 0 AND @updated_at_exists = 0 THEN
        'ALTER TABLE material_tags_relations ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    WHEN @created_at_exists = 0 THEN
        'ALTER TABLE material_tags_relations ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP'
    WHEN @updated_at_exists = 0 THEN
        'ALTER TABLE material_tags_relations ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    ELSE 'SELECT 1'
END;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @created_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'created_at'
);
SET @updated_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'updated_at'
);
SET @sql := CASE
    WHEN @created_at_exists = 0 AND @updated_at_exists = 0 THEN
        'ALTER TABLE `user` ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    WHEN @created_at_exists = 0 THEN
        'ALTER TABLE `user` ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP'
    WHEN @updated_at_exists = 0 THEN
        'ALTER TABLE `user` ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    ELSE 'SELECT 1'
END;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @created_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_tags' AND COLUMN_NAME = 'created_at'
);
SET @updated_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_tags' AND COLUMN_NAME = 'updated_at'
);
SET @sql := CASE
    WHEN @created_at_exists = 0 AND @updated_at_exists = 0 THEN
        'ALTER TABLE user_tags ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    WHEN @created_at_exists = 0 THEN
        'ALTER TABLE user_tags ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP'
    WHEN @updated_at_exists = 0 THEN
        'ALTER TABLE user_tags ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    ELSE 'SELECT 1'
END;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @created_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_tags_relations' AND COLUMN_NAME = 'created_at'
);
SET @updated_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_tags_relations' AND COLUMN_NAME = 'updated_at'
);
SET @sql := CASE
    WHEN @created_at_exists = 0 AND @updated_at_exists = 0 THEN
        'ALTER TABLE user_tags_relations ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    WHEN @created_at_exists = 0 THEN
        'ALTER TABLE user_tags_relations ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP'
    WHEN @updated_at_exists = 0 THEN
        'ALTER TABLE user_tags_relations ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    ELSE 'SELECT 1'
END;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @created_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plan_material_relations' AND COLUMN_NAME = 'created_at'
);
SET @updated_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plan_material_relations' AND COLUMN_NAME = 'updated_at'
);
SET @sql := CASE
    WHEN @created_at_exists = 0 AND @updated_at_exists = 0 THEN
        'ALTER TABLE plan_material_relations ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    WHEN @created_at_exists = 0 THEN
        'ALTER TABLE plan_material_relations ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP'
    WHEN @updated_at_exists = 0 THEN
        'ALTER TABLE plan_material_relations ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    ELSE 'SELECT 1'
END;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @created_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plan_rule_relations' AND COLUMN_NAME = 'created_at'
);
SET @updated_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plan_rule_relations' AND COLUMN_NAME = 'updated_at'
);
SET @sql := CASE
    WHEN @created_at_exists = 0 AND @updated_at_exists = 0 THEN
        'ALTER TABLE plan_rule_relations ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    WHEN @created_at_exists = 0 THEN
        'ALTER TABLE plan_rule_relations ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP'
    WHEN @updated_at_exists = 0 THEN
        'ALTER TABLE plan_rule_relations ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    ELSE 'SELECT 1'
END;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @updated_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event' AND COLUMN_NAME = 'updated_at'
);
SET @sql := IF(@updated_at_exists = 0,
    'ALTER TABLE `event` ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @updated_at_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_message' AND COLUMN_NAME = 'updated_at'
);
SET @sql := IF(@updated_at_exists = 0,
    'ALTER TABLE outbox_message ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- rule.name、user.device_type 按需补充。
SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule' AND COLUMN_NAME = 'name'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `rule` ADD COLUMN name VARCHAR(128) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'device_type'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `user` ADD COLUMN device_type VARCHAR(128) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 将素材标题字段重命名为 name。
SET @title_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material' AND COLUMN_NAME = 'title'
);
SET @name_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material' AND COLUMN_NAME = 'name'
);
SET @sql := IF(@title_exists > 0 AND @name_exists = 0,
    'ALTER TABLE material RENAME COLUMN title TO name',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO schema_version (version, description)
VALUES ('stage-15-add-common-fields', 'add common fields and rename material title to name')
ON DUPLICATE KEY UPDATE description = VALUES(description);
