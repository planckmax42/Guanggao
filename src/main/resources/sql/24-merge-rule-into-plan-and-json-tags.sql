USE ad_platform;

-- 在 23-plan-rule-foreign-key.sql 成功后执行一次，要求 MySQL 8.0.13+。
-- 执行前备份并停止配置、标签写入；使用支持 DELIMITER 的客户端，不使用 --force。
-- 本脚本仅迁移数据库，应用的规则接口、Mapper、ES 索引组装及变更消息需配套调整。
-- MySQL DDL 隐式提交，不能通过一个事务整体回滚；中途失败应核对进度，不要直接重跑。
-- rule 的定向业务字段并入 plan；公共 id/name/advertiser_id/created_at/updated_at
-- 沿用计划自身字段，不以规则的元数据覆盖计划。规则不再作为独立实体维护。
-- 同一规则被多个计划引用时，复制为各计划独立的定向配置。
-- regions 保存地域编码，material.tags 和 user.tag 保存标签值，不保存字典 ID。
-- 空 regions 表示不限地域；空 tags/tag 表示没有标签，应用及 ES 查询需遵守相同语义。
-- 保留 material_tag、user_tag 标签字典，仅删除它们的关联表。

DELIMITER $$

CREATE PROCEDURE migrate_stage_24_plan_targeting()
BEGIN
    IF EXISTS (
        SELECT 1 FROM plan p LEFT JOIN `rule` r ON r.id = p.rule_id
        WHERE p.rule_id IS NOT NULL AND r.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Orphan plan.rule_id: resolve before migration';
    END IF;

    IF EXISTS (
        SELECT 1 FROM `rule` r
        WHERE NOT EXISTS (SELECT 1 FROM plan p WHERE p.rule_id = r.id)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unreferenced rules: assign to plans or archive before migration';
    END IF;

    IF EXISTS (
        SELECT 1 FROM plan p JOIN `rule` r ON r.id = p.rule_id
        WHERE NOT (p.advertiser_id <=> r.advertiser_id)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Plan and rule advertiser mismatch';
    END IF;

    IF EXISTS (
        SELECT 1 FROM rule_region_relation rel
        LEFT JOIN `rule` r ON r.id = rel.rule_id
        LEFT JOIN rule_region region ON region.id = rel.region_id
        WHERE r.id IS NULL OR region.id IS NULL
    ) OR EXISTS (
        SELECT 1 FROM material_tag_relation rel
        LEFT JOIN material m ON m.id = rel.material_id
        LEFT JOIN material_tag t ON t.id = rel.tag_id
        WHERE m.id IS NULL OR t.id IS NULL
    ) OR EXISTS (
        SELECT 1 FROM user_tag_relation rel
        LEFT JOIN `user` u ON u.id = rel.user_id
        LEFT JOIN user_tag t ON t.id = rel.tag_id
        WHERE u.id IS NULL OR t.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Orphan region/tag relations: resolve before migration';
    END IF;

    ALTER TABLE plan
        ADD COLUMN device_type JSON NULL COMMENT '设备定向，与原规则字段语义一致',
        ADD COLUMN gender VARCHAR(32) NULL COMMENT '性别定向，与原规则字段语义一致',
        ADD COLUMN age_min INT NULL COMMENT '最小年龄，含边界',
        ADD COLUMN age_max INT NULL COMMENT '最大年龄，含边界',
        ADD COLUMN regions JSON NOT NULL DEFAULT (JSON_ARRAY()) COMMENT '地域编码数组，空数组表示不限';

    ALTER TABLE material
        ADD COLUMN tags JSON NOT NULL DEFAULT (JSON_ARRAY()) COMMENT '素材标签值数组';
    ALTER TABLE `user`
        ADD COLUMN tag JSON NOT NULL DEFAULT (JSON_ARRAY()) COMMENT '用户标签值数组';

    -- 从字典取实际编码，避免将内部 region_id 当作地域编码写入 ES。
    UPDATE plan p
    LEFT JOIN `rule` r ON r.id = p.rule_id
    LEFT JOIN (
        SELECT rel.rule_id, JSON_ARRAYAGG(region.region_code) AS regions
        FROM rule_region_relation rel
        JOIN rule_region region ON region.id = rel.region_id
        GROUP BY rel.rule_id
    ) region_values ON region_values.rule_id = r.id
    SET p.device_type = r.device_type,
        p.gender = r.gender,
        p.age_min = r.age_min,
        p.age_max = r.age_max,
        p.regions = COALESCE(region_values.regions, JSON_ARRAY()),
        p.updated_at = p.updated_at;

    UPDATE material m
    LEFT JOIN (
        SELECT rel.material_id, JSON_ARRAYAGG(t.tag) AS tags
        FROM material_tag_relation rel
        JOIN material_tag t ON t.id = rel.tag_id
        GROUP BY rel.material_id
    ) tag_values ON tag_values.material_id = m.id
    SET m.tags = COALESCE(tag_values.tags, JSON_ARRAY()),
        m.updated_at = m.updated_at;

    UPDATE `user` u
    LEFT JOIN (
        SELECT rel.user_id, JSON_ARRAYAGG(t.tag) AS tags
        FROM user_tag_relation rel
        JOIN user_tag t ON t.id = rel.tag_id
        GROUP BY rel.user_id
    ) tag_values ON tag_values.user_id = u.id
    SET u.tag = COALESCE(tag_values.tags, JSON_ARRAY()),
        u.updated_at = u.updated_at;

    -- 删除源表前检查字段值和数组元素数。JSON 数组顺序不作为业务语义。
    IF EXISTS (
        SELECT 1 FROM plan p JOIN `rule` r ON r.id = p.rule_id
        WHERE NOT (p.device_type <=> r.device_type)
           OR NOT (p.gender <=> r.gender)
           OR NOT (p.age_min <=> r.age_min)
           OR NOT (p.age_max <=> r.age_max)
           OR JSON_LENGTH(p.regions) <> (
               SELECT COUNT(*) FROM rule_region_relation rel WHERE rel.rule_id = r.id
           )
    ) OR EXISTS (
        SELECT 1 FROM material m
        WHERE JSON_LENGTH(m.tags) <> (
            SELECT COUNT(*) FROM material_tag_relation rel WHERE rel.material_id = m.id
        )
    ) OR EXISTS (
        SELECT 1 FROM `user` u
        WHERE JSON_LENGTH(u.tag) <> (
            SELECT COUNT(*) FROM user_tag_relation rel WHERE rel.user_id = u.id
        )
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Migration validation failed: source tables retained';
    END IF;

    ALTER TABLE plan
        DROP FOREIGN KEY fk_plan_rule,
        DROP INDEX idx_plan_rule_id,
        DROP COLUMN rule_id;

    DROP TABLE rule_region_relation;
    DROP TABLE rule_region;
    DROP TABLE `rule`;
    DROP TABLE material_tag_relation;
    DROP TABLE user_tag_relation;

    INSERT INTO schema_version (version, description)
    VALUES ('stage-24-plan-targeting-json-tags', 'merge rule into plan; inline regions and material/user tags as JSON')
    ON DUPLICATE KEY UPDATE description = VALUES(description);
END$$

DELIMITER ;

CALL migrate_stage_24_plan_targeting();
DROP PROCEDURE migrate_stage_24_plan_targeting;

-- 前置检查失败且尚未发生 DDL 时，处理数据后可先执行以下语句再重试：
-- DROP PROCEDURE IF EXISTS migrate_stage_24_plan_targeting;
