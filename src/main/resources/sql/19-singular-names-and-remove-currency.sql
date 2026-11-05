USE ad_platform;

-- 在 18 号脚本之后执行一次；执行前同步调整应用中引用旧表名、字段名的 SQL。
-- 仅将复数命名改为单数，status、address 等单词保持不变。
-- MySQL DDL 隐式提交，本脚本不能通过事务整体回滚。

-- 钱包调整为每个广告主一个账户。
-- 先建立新唯一约束：若同一广告主已有多个币种钱包，此语句失败，
-- 应先核对并处理这些钱包，不自动合并不同币种的金额。
ALTER TABLE advertiser_wallet
    ADD UNIQUE KEY uk_advertiser_wallet_advertiser (advertiser_id);

ALTER TABLE advertiser_wallet
    DROP INDEX uk_advertiser_wallet_advertiser_currency,
    DROP COLUMN currency,
    RENAME COLUMN balance_micros TO balance_micro,
    RENAME COLUMN frozen_micros TO frozen_micro;

ALTER TABLE plan_budget_state
    RENAME COLUMN spent_micros TO spent_micro,
    RENAME COLUMN reserved_micros TO reserved_micro;

-- 标签表及关联表统一使用单数，保留原有数据。
RENAME TABLE
    material_tags TO material_tag,
    material_tags_relations TO material_tag_relation,
    plan_material_relations TO plan_material_relation,
    plan_rule_relations TO plan_rule_relation,
    rule_region_relations TO rule_region_relation,
    user_tags TO user_tag,
    user_tags_relations TO user_tag_relation;

-- 字段名和关联索引名同步调整。
ALTER TABLE material_tag
    RENAME COLUMN tags TO tag,
    RENAME INDEX uk_material_tags_tags TO uk_material_tag_tag;

ALTER TABLE user_tag
    RENAME COLUMN tags TO tag,
    RENAME INDEX uk_user_tags_tags TO uk_user_tag_tag;

ALTER TABLE material_tag_relation
    RENAME INDEX idx_material_tags_relations_tag_id TO idx_material_tag_relation_tag_id,
    MODIFY COLUMN tag_id BIGINT NOT NULL COMMENT '标签 ID，对应 material_tag.id';

ALTER TABLE user_tag_relation
    RENAME INDEX idx_user_tags_relations_tag_id TO idx_user_tag_relation_tag_id,
    MODIFY COLUMN tag_id BIGINT NOT NULL COMMENT '标签 ID，对应 user_tag.id';

ALTER TABLE plan_material_relation
    RENAME INDEX idx_plan_material_relations_material_id TO idx_plan_material_relation_material_id;

ALTER TABLE plan_rule_relation
    RENAME INDEX idx_plan_rule_relations_rule_id TO idx_plan_rule_relation_rule_id;

ALTER TABLE rule_region_relation
    RENAME INDEX idx_rule_region_relations_region_id TO idx_rule_region_relation_region_id;

INSERT INTO schema_version (version, description)
VALUES ('stage-19-singular-names-and-remove-currency', 'singular table and column names; remove wallet currency')
ON DUPLICATE KEY UPDATE description = VALUES(description);
