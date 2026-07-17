package com.example.adplatform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.adplatform.admin.query.PlanRuleJoinRow;
import com.example.adplatform.admin.entity.PlanEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PlanMapper extends BaseMapper<PlanEntity> {

    @Select("""
            <script>
            SELECT
                p.id AS planId,
                p.user_id AS userId,
                p.name AS planName,
                p.budget_total AS budgetTotal,
                p.budget_daily AS budgetDaily,
                p.bid_price AS bidPrice,
                p.billing_type AS billingType,
                p.start_time AS startTime,
                p.end_time AS endTime,
                p.status AS planStatus,
                p.created_at AS planCreatedAt,
                p.updated_at AS planUpdatedAt,
                r.id AS ruleId,
                r.region AS region,
                r.device_type AS deviceType,
                r.gender AS gender,
                r.age_min AS ageMin,
                r.age_max AS ageMax,
                r.user_tags AS userTags,
                r.created_at AS ruleCreatedAt,
                r.updated_at AS ruleUpdatedAt
            FROM plan p
            LEFT JOIN `rule` r ON r.plan_id = p.id
            WHERE p.id IN
            <foreach collection="planIds" item="planId" open="(" separator="," close=")">
                #{planId}
            </foreach>
            </script>
            """)
    List<PlanRuleJoinRow> selectPlanRuleRows(@Param("planIds") List<Long> planIds);
}
