USE ad_platform;

-- 在 24 号迁移之后执行一次，执行期间停止计划、素材、广告位及关联关系写入。
-- 使用支持 DELIMITER 的 MySQL 客户端，不使用 --force。
-- 删除计划只级联删除关联记录，不删除素材；素材和广告位被引用时禁止删除。
-- 外键仅保证 MySQL 数据关系，ES 文档及 Redis 缓存仍需应用同步清理。
-- DDL 隐式提交，不能整体回滚；若中途失败，核对已有约束后再继续，不要直接重跑。

DELIMITER $$

CREATE PROCEDURE migrate_stage_25_delete_constraints()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM plan_material_relation rel
        LEFT JOIN plan p ON p.id = rel.plan_id
        WHERE p.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Orphan plan_material_relation.plan_id: resolve before migration';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM plan_material_relation rel
        LEFT JOIN material m ON m.id = rel.material_id
        WHERE m.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Orphan plan_material_relation.material_id: resolve before migration';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM material m
        LEFT JOIN slot s ON s.id = m.slot_id
        WHERE m.slot_id IS NOT NULL AND s.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Orphan material.slot_id: resolve before migration';
    END IF;

    ALTER TABLE plan_material_relation
        ADD CONSTRAINT fk_plan_material_plan
            FOREIGN KEY (plan_id) REFERENCES plan (id)
            ON DELETE CASCADE ON UPDATE RESTRICT,
        ADD CONSTRAINT fk_plan_material_material
            FOREIGN KEY (material_id) REFERENCES material (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

    ALTER TABLE material
        ADD CONSTRAINT fk_material_slot
            FOREIGN KEY (slot_id) REFERENCES slot (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

    INSERT INTO schema_version (version, description)
    VALUES ('stage-25-plan-material-slot-foreign-keys',
            'cascade plan relation deletion; restrict referenced material and slot deletion')
    ON DUPLICATE KEY UPDATE description = VALUES(description);
END$$

DELIMITER ;

CALL migrate_stage_25_delete_constraints();
DROP PROCEDURE migrate_stage_25_delete_constraints;

-- 前置检查失败且尚未发生 DDL 时，修正数据后先执行以下语句，再重试脚本：
-- DROP PROCEDURE IF EXISTS migrate_stage_25_delete_constraints;
