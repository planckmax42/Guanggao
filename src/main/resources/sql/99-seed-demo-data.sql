USE ad_platform;

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM daily_report;
DELETE FROM `event`;
DELETE FROM `rule`;
DELETE FROM material;
DELETE FROM plan;
DELETE FROM slot;
DELETE FROM `user`;

ALTER TABLE `user` AUTO_INCREMENT = 1;
ALTER TABLE slot AUTO_INCREMENT = 1;
ALTER TABLE plan AUTO_INCREMENT = 1;
ALTER TABLE material AUTO_INCREMENT = 1;
ALTER TABLE `rule` AUTO_INCREMENT = 1;
ALTER TABLE `event` AUTO_INCREMENT = 1;
ALTER TABLE daily_report AUTO_INCREMENT = 1;

SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO `user`
    (id, name, industry, contact_name, contact_email, slotBloomFilterSnapshot, created_at, updated_at)
VALUES
    (1, '鲜果优选', '生鲜电商', '张经理', 'fresh@example.com', 1, NOW(), NOW()),
    (2, '编程学院', '在线教育', '李经理', 'learn@example.com', 1, NOW(), NOW()),
    (3, '星火游戏', '游戏娱乐', '王经理', 'game@example.com', 1, NOW(), NOW()),
    (4, '停用测试广告主', '测试行业', '赵经理', 'disabled@example.com', 0, NOW(), NOW());

INSERT INTO slot
    (id, slot_code, name, width, height, scene, slotBloomFilterSnapshot, created_at, updated_at)
VALUES
    (1, 'HOME_BANNER', '首页顶部横幅广告位', 1080, 300, 'APP_HOME', 1, NOW(), NOW()),
    (2, 'FEED_CARD', '信息流卡片广告位', 720, 360, 'APP_FEED', 1, NOW(), NOW()),
    (3, 'SEARCH_TEXT', '搜索结果文字广告位', 640, 120, 'APP_SEARCH', 1, NOW(), NOW()),
    (4, 'APP_SPLASH', '开屏广告位', 1080, 1920, 'APP_SPLASH', 0, NOW(), NOW());

-- 金额字段统一按“分”存储：
-- budget_total=1000000 表示总预算 10000 元，bid_price=150 表示出价 1.5 元。
INSERT INTO plan
    (id, user_id, name, budget_total, budget_daily, bid_price, billing_type, start_time, end_time, slotBloomFilterSnapshot, created_at, updated_at)
VALUES
    (1, 1, '鲜果优选-华东拉新计划', 1000000, 200000, 150, 'CPM', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY), 'ONLINE', NOW(), NOW()),
    (2, 2, '编程学院-Java课程转化计划', 800000, 120000, 260, 'CPC', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 20 DAY), 'ONLINE', NOW(), NOW()),
    (3, 3, '星火游戏-暑期预约计划', 1200000, 180000, 220, 'CPA', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 15 DAY), 'PAUSED', NOW(), NOW()),
    (4, 1, '鲜果优选-下月预热计划', 500000, 80000, 120, 'CPC', DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 37 DAY), 'DRAFT', NOW(), NOW()),
    (5, 4, '停用广告主-测试计划', 300000, 50000, 100, 'CPC', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 10 DAY), 'OFFLINE', NOW(), NOW()),
    (6, 3, '星火游戏-安卓用户拉新计划', 900000, 160000, 240, 'CPC', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 25 DAY), 'ONLINE', NOW(), NOW()),
    (7, 1, '鲜果优选-全场景复购计划', 700000, 150000, 180, 'CPC', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 25 DAY), 'ONLINE', NOW(), NOW());

INSERT INTO material
    (id, plan_id, slot_id, title, description, image_url, landing_page_url, audit_status, slotBloomFilterSnapshot, created_at, updated_at)
VALUES
    (1, 1, 1, '鲜果优选新人专享', '新人首单满 99 减 30，当日达生鲜送到家', 'https://cdn.example.com/ad/fresh-home-banner.jpg', 'https://www.example.com/fresh/new-user', 'APPROVED', 1, NOW(), NOW()),
    (2, 1, 2, '周末家庭生鲜补给', '水果、牛奶、蔬菜组合购，家庭用户专属优惠', 'https://cdn.example.com/ad/fresh-feed-card.jpg', 'https://www.example.com/fresh/family', 'APPROVED', 1, NOW(), NOW()),
    (3, 2, 1, 'Java后端训练营', 'Spring Boot、Redis、Kafka 项目实战课程限时优惠', 'https://cdn.example.com/ad/java-home-banner.jpg', 'https://www.example.com/course/java-backend', 'APPROVED', 1, NOW(), NOW()),
    (4, 2, 3, '搜索高意向课程广告', '面向正在搜索编程课程的用户展示', 'https://cdn.example.com/ad/java-search-text.jpg', 'https://www.example.com/course/search-java', 'APPROVED', 1, NOW(), NOW()),
    (5, 3, 2, '星火游戏预约礼包', '暑期新游预约，登录领取稀有道具', 'https://cdn.example.com/ad/game-feed-card.jpg', 'https://www.example.com/game/preorder', 'APPROVED', 1, NOW(), NOW()),
    (6, 4, 1, '下月促销预热素材', '计划尚未上线，因此不会被投放接口返回', 'https://cdn.example.com/ad/fresh-draft.jpg', 'https://www.example.com/fresh/next-month', 'APPROVED', 1, NOW(), NOW()),
    (7, 2, 2, '未审核课程素材', '素材还在审核中，因此不会被召回', 'https://cdn.example.com/ad/java-pending.jpg', 'https://www.example.com/course/pending', 'PENDING', 1, NOW(), NOW()),
    (8, 1, 1, '已停用生鲜素材', '素材状态已停用，因此不会被召回', 'https://cdn.example.com/ad/fresh-disabled.jpg', 'https://www.example.com/fresh/disabled', 'APPROVED', 0, NOW(), NOW()),
    (9, 6, 2, '星火游戏安卓预约礼包', '安卓用户预约送限定礼包', 'https://cdn.example.com/ad/game-android-feed.jpg', 'https://www.example.com/game/android', 'APPROVED', 1, NOW(), NOW()),
    (10, 6, 1, '星火游戏首页预约', '首页大图展示新游预约活动', 'https://cdn.example.com/ad/game-home-banner.jpg', 'https://www.example.com/game/home', 'APPROVED', 1, NOW(), NOW()),
    (11, 7, 1, '鲜果优选会员日', '会员日全场满减，适合首页横幅曝光', 'https://cdn.example.com/ad/fresh-member-banner.jpg', 'https://www.example.com/fresh/member', 'APPROVED', 1, NOW(), NOW()),
    (12, 7, 2, '鲜果优选晚餐组合', '下班前推荐晚餐生鲜组合', 'https://cdn.example.com/ad/fresh-dinner-feed.jpg', 'https://www.example.com/fresh/dinner', 'APPROVED', 1, NOW(), NOW()),
    (13, 7, 3, '搜索生鲜优惠广告', '搜索场景下展示高意向生鲜优惠', 'https://cdn.example.com/ad/fresh-search-text.jpg', 'https://www.example.com/fresh/search', 'APPROVED', 1, NOW(), NOW());

INSERT INTO `rule`
    (id, plan_id, region, device_type, gender, age_min, age_max, user_tags, created_at, updated_at)
VALUES
    (1, 1, '["BEIJING","SHANGHAI","HANGZHOU"]', '["IOS","ANDROID"]', NULL, 18, 45, '["fresh","shopping","family"]', NOW(), NOW()),
    (2, 2, '["BEIJING","SHENZHEN","GUANGZHOU"]', '["IOS","ANDROID","WEB"]', NULL, 16, 35, '["education","student","programming","java"]', NOW(), NOW()),
    (3, 3, '["BEIJING","SHANGHAI","GUANGZHOU"]', '["ANDROID","IOS"]', 'MALE', 18, 30, '["game","acg"]', NOW(), NOW()),
    (4, 4, NULL, NULL, NULL, NULL, NULL, NULL, NOW(), NOW()),
    (5, 5, NULL, NULL, NULL, NULL, NULL, NULL, NOW(), NOW()),
    (6, 6, '["BEIJING","SHANGHAI","GUANGZHOU"]', '["ANDROID"]', 'MALE', 18, 30, '["game","acg"]', NOW(), NOW());

INSERT INTO schema_version (version, description)
VALUES ('demo-data', 'demo seed data for admin delivery tracking and report')
ON DUPLICATE KEY UPDATE description = VALUES(description);
