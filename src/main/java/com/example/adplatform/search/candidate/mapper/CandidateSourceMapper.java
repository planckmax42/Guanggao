package com.example.adplatform.search.candidate.mapper;

import com.example.adplatform.search.candidate.dto.CandidateSourceRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CandidateSourceMapper {

    String COLUMNS = """
            m.id AS materialId, m.plan_id AS planId, p.user_id AS userId,
            m.slot_id AS slotId, s.slot_code AS slotCode,
            m.title, m.description, m.image_url AS imageUrl,
            m.landing_page_url AS landingPageUrl, m.status AS materialStatus,
            m.audit_status AS auditStatus, p.status AS planStatus,
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

    String ELIGIBLE = """
            m.status = 1 AND m.audit_status = 'APPROVED'
            AND p.status = 'ONLINE' AND s.status = 1
            """;

    @Select("SELECT " + COLUMNS + FROM + " WHERE " + ELIGIBLE + " ORDER BY m.id")
    List<CandidateSourceRow> selectAllEligible();

    @Select("SELECT " + COLUMNS + FROM + " WHERE " + ELIGIBLE + " AND m.id = #{materialId}")
    CandidateSourceRow selectEligibleByMaterialId(@Param("materialId") Long materialId);

    @Select("SELECT " + COLUMNS + FROM + " WHERE " + ELIGIBLE + " AND p.id = #{planId} ORDER BY m.id")
    List<CandidateSourceRow> selectEligibleByPlanId(@Param("planId") Long planId);

    @Select("SELECT " + COLUMNS + FROM + " WHERE " + ELIGIBLE + " AND s.id = #{slotId} ORDER BY m.id")
    List<CandidateSourceRow> selectEligibleBySlotId(@Param("slotId") Long slotId);

    @Select("SELECT " + COLUMNS + FROM + " WHERE " + ELIGIBLE + " AND s.slot_code = #{slotCode} ORDER BY m.id DESC")
    List<CandidateSourceRow> selectEligibleBySlotCode(@Param("slotCode") String slotCode);
}
