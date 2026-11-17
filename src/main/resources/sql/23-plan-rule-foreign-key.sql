USE ad_platform;

-- 在 21 号结构迁移之后执行一次；22 号脚本是清空数据的维护脚本，不是本脚本的前置条件。
-- 执行期间停止计划和规则写入，应用需同步改为读写 plan.rule_id。
-- 每个计划最多关联一条规则；NULL 表示未配置规则，同一规则可以被多个计划引用。
-- 迁移成功后删除旧关联表。MySQL DDL 隐式提交，本脚本不能通过事务整体回滚。
-- 使用支持 DELIMITER 的 MySQL 客户端执行。

DELIMITER $$

CREATE PROCEDURE migrate_stage_23_plan_rule()
BEGIN
    -- 多条规则不能无损合并，必须先明确每个计划要保留的规则。
    IF EXISTS (
        SELECT plan_id
        FROM plan_rule_relation
        GROUP BY plan_id
        HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Multiple rules per plan: resolve plan_rule_relation before migration';
    END IF;

    -- 原关联表没有外键，迁移前检查悬空关联，避免遗漏数据。
    IF EXISTS (
        SELECT 1
        FROM plan_rule_relation pr
        LEFT JOIN plan p ON p.id = pr.plan_id
        LEFT JOIN `rule` r ON r.id = pr.rule_id
        WHERE p.id IS NULL OR r.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Orphan plan/rule references: resolve plan_rule_relation before migration';
    END IF;

    ALTER TABLE plan
        ADD COLUMN rule_id BIGINT NULL COMMENT '定向规则 ID，对应 rule.id',
        ADD KEY idx_plan_rule_id (rule_id),
        ADD CONSTRAINT fk_plan_rule
            FOREIGN KEY (rule_id) REFERENCES `rule` (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

    UPDATE plan p
    JOIN plan_rule_relation pr ON pr.plan_id = p.id
    SET p.rule_id = pr.rule_id,
        p.updated_at = p.updated_at;

    DROP TABLE plan_rule_relation;

    INSERT INTO schema_version (version, description)
    VALUES ('stage-23-plan-rule-foreign-key', 'replace plan rule relation with plan.rule_id foreign key')
    ON DUPLICATE KEY UPDATE description = VALUES(description);
END$$

DELIMITER ;

CALL migrate_stage_23_plan_rule();
DROP PROCEDURE migrate_stage_23_plan_rule;

-- 若前置检查失败，修正关联数据后先执行：
-- DROP PROCEDURE IF EXISTS migrate_stage_23_plan_rule;
-- 再重新执行本脚本；若已经发生 DDL 变更，应先核对迁移进度，不要直接重跑。
