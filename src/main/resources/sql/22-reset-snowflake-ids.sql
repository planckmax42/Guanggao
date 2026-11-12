USE ad_platform;

-- 一次性重置脚本：清空全部业务数据、删除所有 public_id 列、五张管理表主键改由应用雪花生成器分配。
-- 执行前必须先 mysqldump 备份。重复执行安全（TRUNCATE + 幂等 DDL）。
--
-- 关于外键：项目已弃用物理外键（见提交"低流量管理接口弃用逻辑外键"），
-- 本脚本不重建外键约束；所有关联列（advertiser_id/slot_id/plan_id/material_id/rule_id）
-- 保持 BIGINT，新数据一律写入雪花主键值，关联完整性由应用保证。
--
-- 其余带 AUTO_INCREMENT 的表（event/charge_record/daily_report/advertiser_assistant/
-- advertiser_wallet/rule_region/material_tag/user_tag/outbox_message/schema_version）
-- 对应模块尚未迁移到雪花主键，本次保持自增不动。

SET FOREIGN_KEY_CHECKS = 0;

-- 1. 清空数据，仅保留表结构；schema_version 迁移记录保留。
TRUNCATE TABLE advertiser;
TRUNCATE TABLE advertiser_assistant;
TRUNCATE TABLE advertiser_wallet;
TRUNCATE TABLE charge_record;
TRUNCATE TABLE daily_report;
TRUNCATE TABLE `event`;
TRUNCATE TABLE material;
TRUNCATE TABLE material_tag;
TRUNCATE TABLE material_tag_relation;
TRUNCATE TABLE outbox_message;
TRUNCATE TABLE plan;
TRUNCATE TABLE plan_budget_state;
TRUNCATE TABLE plan_material_relation;
TRUNCATE TABLE plan_rule_relation;
TRUNCATE TABLE `rule`;
TRUNCATE TABLE rule_region;
TRUNCATE TABLE rule_region_relation;
TRUNCATE TABLE slot;
TRUNCATE TABLE `user`;
TRUNCATE TABLE user_tag;
TRUNCATE TABLE user_tag_relation;

-- 2. 删除所有 public_id 列；其唯一索引（uk_*_public_id）随列自动删除。
--    共 6 张表：五张管理表 + advertiser_assistant（无 Java 代码，一并清除）。

SET @column_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'advertiser' AND COLUMN_NAME = 'public_id');
SET @sql := IF(@column_exists > 0, 'ALTER TABLE advertiser DROP COLUMN public_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slot' AND COLUMN_NAME = 'public_id');
SET @sql := IF(@column_exists > 0, 'ALTER TABLE slot DROP COLUMN public_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plan' AND COLUMN_NAME = 'public_id');
SET @sql := IF(@column_exists > 0, 'ALTER TABLE plan DROP COLUMN public_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material' AND COLUMN_NAME = 'public_id');
SET @sql := IF(@column_exists > 0, 'ALTER TABLE material DROP COLUMN public_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule' AND COLUMN_NAME = 'public_id');
SET @sql := IF(@column_exists > 0, 'ALTER TABLE `rule` DROP COLUMN public_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'advertiser_assistant' AND COLUMN_NAME = 'public_id');
SET @sql := IF(@column_exists > 0, 'ALTER TABLE advertiser_assistant DROP COLUMN public_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 五张管理表 id 去掉 AUTO_INCREMENT，主键值由应用雪花生成器分配
--    （admin.id.worker-id / admin.id.datacenter-id，见 AdminIdConfiguration）。
ALTER TABLE advertiser MODIFY id BIGINT NOT NULL;
ALTER TABLE slot MODIFY id BIGINT NOT NULL;
ALTER TABLE plan MODIFY id BIGINT NOT NULL;
ALTER TABLE material MODIFY id BIGINT NOT NULL;
ALTER TABLE `rule` MODIFY id BIGINT NOT NULL;

SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO schema_version (version, description)
VALUES ('stage-22-snowflake-id-reset', 'clear all data, drop all public_id, admin ids assigned by application snowflake generator')
ON DUPLICATE KEY UPDATE description = VALUES(description);