USE ad_platform;

-- 在 20 号脚本之后执行一次；现有非空 device_type 必须为合法 JSON。
-- 规则设备类型保存 JSON 数组，例如 ["ANDROID", "IOS"]。
ALTER TABLE `rule` MODIFY COLUMN device_type JSON NULL;

-- 用户设备类型同样改为 JSON；单个设备值应使用 JSON 字符串，例如 "ANDROID"。
ALTER TABLE `user` MODIFY COLUMN device_type JSON NULL;

INSERT INTO schema_version (version, description)
VALUES ('stage-21-device-type-json', 'convert rule and user device_type columns to JSON')
ON DUPLICATE KEY UPDATE description = VALUES(description);
