USE ad_platform;

-- 地域字典，每行保存一个地域编码。
CREATE TABLE IF NOT EXISTS rule_region (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    region_code VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rule_region_region_code (region_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 定向规则与地域的多对多关联。
CREATE TABLE IF NOT EXISTS rule_region_relations (
    rule_id BIGINT NOT NULL COMMENT '定向规则 ID，对应 rule.id',
    region_id BIGINT NOT NULL COMMENT '地域 ID，对应 rule_region.id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (rule_id, region_id),
    KEY idx_rule_region_relations_region_id (region_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 将 rule.region 中的 JSON 数组拆分为地域字典。NULL 或空数组表示不限地域。
INSERT IGNORE INTO rule_region (region_code)
SELECT DISTINCT TRIM(region_item.region_code)
FROM `rule` r
CROSS JOIN JSON_TABLE(
    COALESCE(r.region, JSON_ARRAY()),
    '$[*]' COLUMNS(region_code VARCHAR(64) PATH '$')
) AS region_item
WHERE NULLIF(TRIM(region_item.region_code), '') IS NOT NULL;

-- 根据原有 JSON 数据建立规则与地域关联。
INSERT IGNORE INTO rule_region_relations (rule_id, region_id)
SELECT r.id, rr.id
FROM `rule` r
CROSS JOIN JSON_TABLE(
    COALESCE(r.region, JSON_ARRAY()),
    '$[*]' COLUMNS(region_code VARCHAR(64) PATH '$')
) AS region_item
INNER JOIN rule_region rr
    ON rr.region_code = TRIM(region_item.region_code) COLLATE utf8mb4_unicode_ci
WHERE NULLIF(TRIM(region_item.region_code), '') IS NOT NULL;

INSERT INTO schema_version (version, description)
VALUES ('stage-17-rule-region-relations', 'normalize rule regions and migrate existing region data')
ON DUPLICATE KEY UPDATE description = VALUES(description);
