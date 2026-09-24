USE ad_platform;

-- 广告计划与素材的关联。
CREATE TABLE IF NOT EXISTS plan_material_relations (
    plan_id BIGINT NOT NULL COMMENT '广告计划 ID，对应 plan.id',
    material_id BIGINT NOT NULL COMMENT '素材 ID，对应 material.id',
    PRIMARY KEY (plan_id, material_id),
    KEY idx_plan_material_relations_material_id (material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 广告计划与定向规则的关联。
CREATE TABLE IF NOT EXISTS plan_rule_relations (
    plan_id BIGINT NOT NULL COMMENT '广告计划 ID，对应 plan.id',
    rule_id BIGINT NOT NULL COMMENT '定向规则 ID，对应 rule.id',
    PRIMARY KEY (plan_id, rule_id),
    KEY idx_plan_rule_relations_rule_id (rule_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO schema_version (version, description)
VALUES ('stage-13-plan-relations', 'plan material and rule relations schema')
ON DUPLICATE KEY UPDATE description = VALUES(description);
