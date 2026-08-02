USE ad_platform;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'material'
      AND COLUMN_NAME = 'slot_id'
);
SET @add_column_sql := IF(
    @column_exists = 0,
    'ALTER TABLE material ADD COLUMN slot_id BIGINT NULL AFTER plan_id',
    'SELECT 1'
);
PREPARE add_column_stmt FROM @add_column_sql;
EXECUTE add_column_stmt;
DEALLOCATE PREPARE add_column_stmt;

SET @index_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'material'
      AND INDEX_NAME = 'idx_material_slot_status_audit'
);
SET @add_index_sql := IF(
    @index_exists = 0,
    'CREATE INDEX idx_material_slot_status_audit ON material (slot_id, slotBloomFilterSnapshot, audit_status)',
    'SELECT 1'
);
PREPARE add_index_stmt FROM @add_index_sql;
EXECUTE add_index_stmt;
DEALLOCATE PREPARE add_index_stmt;

INSERT INTO schema_version (version, description)
VALUES ('stage-2', 'delivery schema')
ON DUPLICATE KEY UPDATE description = VALUES(description);
