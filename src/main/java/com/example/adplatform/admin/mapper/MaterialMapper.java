package com.example.adplatform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.query.MaterialPlanJoinRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MaterialMapper extends BaseMapper<MaterialEntity> {

    /**
     * 一次查询返回事件处理所需的素材和计划字段。
     *
     * <p>使用 LEFT JOIN 保留孤立素材行，调用方可通过 {@code planId == null}
     * 识别逻辑外键指向的计划不存在。</p>
     */
    @Select("""
            SELECT
                m.id AS materialId,
                m.plan_id AS materialPlanId,
                m.slot_id AS slotId,
                p.id AS planId,
                p.budget_total AS budgetTotal,
                p.budget_daily AS budgetDaily,
                p.bid_price AS bidPrice,
                p.billing_type AS billingType
            FROM material m
            LEFT JOIN plan p ON p.id = m.plan_id
            WHERE m.id = #{materialId}
            """)
    MaterialPlanJoinRow selectMaterialPlanById(@Param("materialId") Long materialId);
}
