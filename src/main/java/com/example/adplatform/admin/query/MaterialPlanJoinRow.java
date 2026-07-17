package com.example.adplatform.admin.query;

import lombok.Getter;
import lombok.Setter;

/**
 * 素材与计划联表查询的事件处理投影。
 *
 * <p>{@code planId} 在 LEFT JOIN 未命中计划时为 {@code null}，用于区分
 * “素材不存在”和“素材关联的计划不存在”。</p>
 */
@Getter
@Setter
public class MaterialPlanJoinRow {

    private Long materialId;
    private Long materialPlanId;
    private Long slotId;

    private Long planId;
    private Long budgetTotal;
    private Long budgetDaily;
    private Long bidPrice;
    private String billingType;
}
