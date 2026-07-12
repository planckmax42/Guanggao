package com.example.adplatform.infra.redis;

import com.example.adplatform.admin.entity.PlanEntity;

import java.time.LocalDate;

public interface BudgetRedisService {

    /**
     * 召回阶段预算粗过滤：预算还有余额才允许计划继续参与召回。
     */
    boolean hasAvailableBudget(PlanEntity plan, LocalDate statDate);

    /**
     * 扣费阶段实时预算扣减：预算足够时原子累加已消耗金额。
     */
    boolean tryCharge(PlanEntity plan, LocalDate statDate, long amount);

    /**
     * 根据 charge_record 成功扣费流水重建指定计划的预算消耗缓存。
     */
    void rebuildBudget(PlanEntity plan, LocalDate statDate);
}
