package com.example.adplatform.search.candidate.mapper;

import com.example.adplatform.search.candidate.query.CandidateSourceRow;
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

    /** 与 {@link CandidateSourceRow} 字段一一对应的去范式化投影。 */
    String COLUMNS = """
            m.id AS materialId, m.public_id AS materialPublicId,
            m.plan_id AS planId, p.public_id AS planPublicId, p.user_id AS userId,
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
    List<CandidateSourceRow> selectAllEligible();

    @Select("SELECT " + COLUMNS + FROM + " WHERE " + ELIGIBLE + " AND m.public_id = #{materialPublicId}")
    CandidateSourceRow selectEligibleByMaterialPublicId(@Param("materialPublicId") String materialPublicId);

    @Select("SELECT " + COLUMNS + FROM + " WHERE " + ELIGIBLE + " AND p.public_id = #{planPublicId} ORDER BY m.id")
    List<CandidateSourceRow> selectEligibleByPlanPublicId(@Param("planPublicId") String planPublicId);

    @Select("SELECT " + COLUMNS + FROM + " WHERE " + ELIGIBLE + " AND s.public_id = #{slotPublicId} ORDER BY m.id")
    List<CandidateSourceRow> selectEligibleBySlotPublicId(@Param("slotPublicId") String slotPublicId);

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
    List<CandidateSourceRow> selectEligibleBySlotCode(@Param("slotCode") String slotCode);
}
