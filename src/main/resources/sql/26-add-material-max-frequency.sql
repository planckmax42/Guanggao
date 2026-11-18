USE ad_platform;

-- 在 25 号迁移之后执行；0 表示不限制，正数表示同一用户每天对该素材的曝光上限。
SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'material'
      AND COLUMN_NAME = 'max_frequency'
);
SET @add_column_sql := IF(
    @column_exists = 0,
    'ALTER TABLE material ADD COLUMN max_frequency INT NOT NULL DEFAULT 0 COMMENT ''单用户每日曝光上限，0 表示不限制''',
    'SELECT 1'
);
PREPARE add_column_stmt FROM @add_column_sql;
EXECUTE add_column_stmt;
DEALLOCATE PREPARE add_column_stmt;

INSERT INTO schema_version (version, description)
VALUES ('stage-26-add-material-max-frequency', 'add material daily per-user frequency limit')
ON DUPLICATE KEY UPDATE description = VALUES(description);
