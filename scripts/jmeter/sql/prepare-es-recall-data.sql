USE ad_platform;

-- 在同一 MySQL 会话中先设置 @candidate_count，可选 1000 / 5000 / 10000。
-- 未设置或值不合法时默认生成 1000 条可投放候选文档。
SET @candidate_count = CASE
    WHEN @candidate_count IN (1000, 5000, 10000) THEN @candidate_count
    ELSE 1000
END;

SET SESSION cte_max_recursion_depth = 10050;

DROP TEMPORARY TABLE IF EXISTS tmp_es_candidate_seq;
CREATE TEMPORARY TABLE tmp_es_candidate_seq (
    n INT PRIMARY KEY
) ENGINE = MEMORY;

INSERT INTO tmp_es_candidate_seq (n)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < @candidate_count
)
SELECT n FROM seq;

-- 只清理本脚本使用的 ID 段，不会动演示数据和业务数据。
DELETE FROM `rule` WHERE id BETWEEN 900001 AND 910000;
DELETE FROM material WHERE id BETWEEN 900001 AND 910000;
DELETE FROM plan WHERE id BETWEEN 900001 AND 910000;
DELETE FROM slot WHERE id BETWEEN 900001 AND 900003;
DELETE FROM `user` WHERE id = 900000;

INSERT INTO `user`
    (id, name, industry, contact_name, contact_email, slotBloomFilterSnapshot, created_at, updated_at)
VALUES
    (900000, 'ES召回压测广告主', '性能测试', '压测管理员', 'es-load-test@example.com', 1, NOW(), NOW());

INSERT INTO slot
    (id, slot_code, name, width, height, scene, slotBloomFilterSnapshot, created_at, updated_at)
VALUES
    (900001, 'ES_LOAD_HOME', 'ES压测首页广告位', 1080, 300, 'LOAD_TEST', 1, NOW(), NOW()),
    (900002, 'ES_LOAD_FEED', 'ES压测信息流广告位', 720, 360, 'LOAD_TEST', 1, NOW(), NOW()),
    (900003, 'ES_LOAD_SEARCH', 'ES压测搜索广告位', 640, 120, 'LOAD_TEST', 1, NOW(), NOW());

INSERT INTO plan
    (id, user_id, name, budget_total, budget_daily, bid_price, billing_type,
     start_time, end_time, slotBloomFilterSnapshot, created_at, updated_at)
SELECT
    900000 + n,
    900000,
    CONCAT('ES召回压测计划-', LPAD(n, 5, '0')),
    100000000,
    10000000,
    50 + MOD(n * 37, 950),
    CASE MOD(n, 3) WHEN 0 THEN 'CPM' WHEN 1 THEN 'CPC' ELSE 'CPA' END,
    DATE_SUB(NOW(), INTERVAL 1 DAY),
    DATE_ADD(NOW(), INTERVAL 90 DAY),
    'ONLINE',
    NOW(),
    NOW()
FROM tmp_es_candidate_seq;

INSERT INTO material
    (id, plan_id, slot_id, title, description, image_url, landing_page_url,
     audit_status, slotBloomFilterSnapshot, created_at, updated_at)
SELECT
    900000 + n,
    900000 + n,
    900001 + MOD(n - 1, 3),
    CONCAT('ES召回压测素材-', LPAD(n, 5, '0')),
    CONCAT('用于验证多维定向和粗召回性能，序号 ', n),
    CONCAT('https://cdn.example.com/es-load/', n, '.jpg'),
    CONCAT('https://www.example.com/es-load/', n),
    'APPROVED',
    1,
    NOW(),
    NOW()
FROM tmp_es_candidate_seq;

INSERT INTO `rule`
    (id, plan_id, region, device_type, gender, age_min, age_max, user_tags,
     created_at, updated_at)
SELECT
    900000 + n,
    900000 + n,
    CASE
        WHEN MOD(n, 10) = 0 THEN NULL
        WHEN MOD(n, 3) = 0 THEN '["BEIJING","SHANGHAI"]'
        WHEN MOD(n, 3) = 1 THEN '["SHENZHEN","GUANGZHOU"]'
        ELSE '["HANGZHOU","CHENGDU"]'
    END,
    CASE
        WHEN MOD(n, 8) = 0 THEN NULL
        WHEN MOD(n, 2) = 0 THEN '["IOS","WEB"]'
        ELSE '["ANDROID"]'
    END,
    CASE WHEN MOD(n, 4) = 0 THEN 'FEMALE'
         WHEN MOD(n, 4) = 1 THEN 'MALE'
         ELSE NULL END,
    CASE WHEN MOD(n, 7) = 0 THEN NULL ELSE 18 + MOD(n, 8) END,
    CASE WHEN MOD(n, 7) = 0 THEN NULL ELSE 40 + MOD(n, 16) END,
    CASE
        WHEN MOD(n, 9) = 0 THEN NULL
        WHEN MOD(n, 3) = 0 THEN '["fresh","family","shopping"]'
        WHEN MOD(n, 3) = 1 THEN '["education","java","student"]'
        ELSE '["game","acg","mobile"]'
    END,
    NOW(),
    NOW()
FROM tmp_es_candidate_seq;

SELECT
    @candidate_count AS generated_candidates,
    COUNT(*) AS eligible_materials,
    COUNT(DISTINCT plan_id) AS eligible_plans,
    COUNT(DISTINCT slot_id) AS load_test_slots
FROM material
WHERE id BETWEEN 900001 AND 900000 + @candidate_count
  AND slotBloomFilterSnapshot = 1
  AND audit_status = 'APPROVED';

DROP TEMPORARY TABLE tmp_es_candidate_seq;
