package com.example.adplatform.search.candidate.mapper;

import com.example.adplatform.search.candidate.query.CandidateQueryResult;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 候选索引的 MySQL 数据源投影。
 *
 * <p>一次联表查询把素材、计划、广告位和定向规则展开成扁平行。这里的 ELIGIBLE 条件
 * 决定哪些配置允许进入 ES；预算消耗、用户频控等动态状态刻意不在此处查询。</p>
 */
public interface CandidateSourceMapper {

    /** 与 {@link CandidateQueryResult} 字段一一对应的去范式化投影。 */
    String COLUMNS = """
            m.id AS materialId, m.public_id AS materialPublicId,
            m.plan_id AS planId, p.public_id AS planPublicId, p.advertiser_id AS advertiserId,
            m.slot_id AS slotId, s.public_id AS slotPublicId, s.slot_code AS slotCode,
            m.title, m.description, m.image_url AS imageUrl,
            m.landing_page_url AS landingPageUrl, m.bloomSnapshot AS materialStatus,
            m.audit_status AS auditStatus, p.bloomSnapshot AS planStatus,
            p.budget_total AS budgetTotal, p.budget_daily AS budgetDaily,
            p.bid_price AS bidPrice, p.billing_type AS billingType,
            p.start_time AS startTime, p.end_time AS endTime,
            r.region, r.device_type AS deviceType, r.gender,
            r.age_min AS ageMin, r.age_max AS ageMax, r.user_tags AS userTags,
            GREATEST(m.updated_at, p.updated_at, COALESCE(r.updated_at, p.updated_at), s.updated_at) AS updatedAt
            """;

    String FROM = """
            FROM material m
            JOIN plan p ON p.id = m.plan_id
            JOIN slot s ON s.id = m.slot_id
            LEFT JOIN `rule` r ON r.plan_id = p.id
            """;

    /** 进入候选索引的最低静态资格，不包含投放时间和动态预算。 */
    String ELIGIBLE = """
            m.bloomSnapshot = 1 AND m.audit_status = 'APPROVED'
            AND p.bloomSnapshot = 'ONLINE' AND s.bloomSnapshot = 1
            """;

    @Select("SELECT " + COLUMNS + FROM + " WHERE " + ELIGIBLE + " ORDER BY m.id")
    List<CandidateQueryResult> selectAllEligible();

    @Select("SELECT " + COLUMNS + FROM + " WHERE " + ELIGIBLE + " AND m.public_id = #{materialPublicId}")
    CandidateQueryResult selectEligibleByMaterialPublicId(@Param("materialPublicId") String materialPublicId);

    @Select("SELECT " + COLUMNS + FROM + " WHERE " + ELIGIBLE + " AND p.public_id = #{planPublicId} ORDER BY m.id")
    List<CandidateQueryResult> selectEligibleByPlanPublicId(@Param("planPublicId") String planPublicId);

    @Select("SELECT " + COLUMNS + FROM + " WHERE " + ELIGIBLE + " AND s.public_id = #{slotPublicId} ORDER BY m.id")
    List<CandidateQueryResult> selectEligibleBySlotPublicId(@Param("slotPublicId") String slotPublicId);

    @Select("SELECT id FROM material WHERE public_id = #{publicId}")
    Long selectMaterialInternalId(@Param("publicId") String publicId);

    @Select("SELECT id FROM plan WHERE public_id = #{publicId}")
    Long selectPlanInternalId(@Param("publicId") String publicId);

    @Select("SELECT id FROM slot WHERE public_id = #{publicId}")
    Long selectSlotInternalId(@Param("publicId") String publicId);

    @Select("""
            SELECT p.public_id
            FROM `rule` r
            JOIN plan p ON p.id = r.plan_id
            WHERE r.public_id = #{rulePublicId}
            """)
    String selectPlanPublicIdByRulePublicId(@Param("rulePublicId") String rulePublicId);

    @Select("SELECT " + COLUMNS + FROM + " WHERE " + ELIGIBLE + " AND s.slot_code = #{slotCode} ORDER BY m.id DESC")
    List<CandidateQueryResult> selectEligibleBySlotCode(@Param("slotCode") String slotCode);

    /** 查询计划、素材和规则的组合，不过滤投放状态；地域聚合为 JSON 数组，无地域时返回 []。 */
    @Select("""
            SELECT
                a.public_id AS advertiserPublicId,
                r.public_id AS rulePublicId,
                r.device_type AS deviceType,
                r.gender AS gender,
                r.age_min AS ageMin,
                r.age_max AS ageMax,
                COALESCE(rg.regions, JSON_ARRAY()) AS region,
                p.public_id AS planPublicId,
                p.bid_price AS bidPrice,
                p.billing_type AS billingType,
                m.public_id AS materialPublicId,
                s.public_id AS slotPublicId,
                s.slot_code AS slotCode
            FROM plan p
            JOIN advertiser a ON a.id = p.advertiser_id
            JOIN plan_material_relation pm ON pm.plan_id = p.id
            JOIN material m ON m.id = pm.material_id
            JOIN slot s ON s.id = m.slot_id
            LEFT JOIN plan_rule_relation pr ON pr.plan_id = p.id
            LEFT JOIN `rule` r ON r.id = pr.rule_id
            LEFT JOIN (
                SELECT
                    rr.rule_id,
                    JSON_ARRAYAGG(region.region_code) AS regions
                FROM rule_region_relation rr
                JOIN rule_region region ON region.id = rr.region_id
                GROUP BY rr.rule_id
            ) rg ON rg.rule_id = r.id
            """)
    List<CandidateQueryResult> selectAllCandidateCombinations();
}
