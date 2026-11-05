USE ad_platform;

-- 在 17 号脚本完成地域数据迁移、19 号脚本完成表名调整后执行一次。
-- 地域数据由 rule_region 和 rule_region_relation 维护。
-- 执行前需将应用中剩余的 rule.region 读写改为使用地域关联表。
ALTER TABLE `rule` DROP COLUMN region;

INSERT INTO schema_version (version, description)
VALUES ('stage-20-remove-rule-region', 'remove legacy rule region column')
ON DUPLICATE KEY UPDATE description = VALUES(description);
