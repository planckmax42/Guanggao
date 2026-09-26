USE ad_platform;

-- 素材归属广告主：先增加可空列，从计划关联回填后再收紧为非空。
SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material' AND COLUMN_NAME = 'advertiser_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE material ADD COLUMN advertiser_id BIGINT NULL AFTER public_id',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE material m
LEFT JOIN (
    SELECT pmr.material_id, MIN(p.advertiser_id) AS advertiser_id
    FROM plan_material_relations pmr
    INNER JOIN plan p ON p.id = pmr.plan_id
    GROUP BY pmr.material_id
    HAVING COUNT(DISTINCT p.advertiser_id) = 1
) owner ON owner.material_id = m.id
SET m.advertiser_id = owner.advertiser_id
WHERE m.advertiser_id IS NULL;

SET @column_nullable := (
    SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material' AND COLUMN_NAME = 'advertiser_id'
);
SET @sql := IF(@column_nullable = 'YES',
    'ALTER TABLE material MODIFY COLUMN advertiser_id BIGINT NOT NULL AFTER public_id',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material' AND INDEX_NAME = 'idx_material_advertiser_id'
);
SET @sql := IF(@index_exists = 0,
    'ALTER TABLE material ADD INDEX idx_material_advertiser_id (advertiser_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 定向规则归属广告主，回填逻辑与素材一致。
SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule' AND COLUMN_NAME = 'advertiser_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `rule` ADD COLUMN advertiser_id BIGINT NULL AFTER public_id',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `rule` r
LEFT JOIN (
    SELECT prr.rule_id, MIN(p.advertiser_id) AS advertiser_id
    FROM plan_rule_relations prr
    INNER JOIN plan p ON p.id = prr.plan_id
    GROUP BY prr.rule_id
    HAVING COUNT(DISTINCT p.advertiser_id) = 1
) owner ON owner.rule_id = r.id
SET r.advertiser_id = owner.advertiser_id
WHERE r.advertiser_id IS NULL;

SET @column_nullable := (
    SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule' AND COLUMN_NAME = 'advertiser_id'
);
SET @sql := IF(@column_nullable = 'YES',
    'ALTER TABLE `rule` MODIFY COLUMN advertiser_id BIGINT NOT NULL AFTER public_id',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule' AND INDEX_NAME = 'idx_rule_advertiser_id'
);
SET @sql := IF(@index_exists = 0,
    'ALTER TABLE `rule` ADD INDEX idx_rule_advertiser_id (advertiser_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 计划预算实时状态，plan_id 同时作为主键和计划逻辑关联键。
CREATE TABLE IF NOT EXISTS plan_budget_state (
    plan_id BIGINT NOT NULL,
    spent_micros BIGINT NOT NULL DEFAULT 0,
    reserved_micros BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT IGNORE INTO plan_budget_state (plan_id, spent_micros, reserved_micros)
SELECT id, 0, 0 FROM plan;

-- 广告主币种钱包，同一广告主的同一币种只允许一个账户。
CREATE TABLE IF NOT EXISTS advertiser_wallet (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    advertiser_id BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    balance_micros BIGINT NOT NULL DEFAULT 0,
    frozen_micros BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_advertiser_wallet_advertiser_currency (advertiser_id, currency)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO schema_version (version, description)
VALUES ('stage-16-add-advertiser-budget-wallet', 'add advertiser ownership, plan budget state and advertiser wallet')
ON DUPLICATE KEY UPDATE description = VALUES(description);
