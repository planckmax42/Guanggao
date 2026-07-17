package com.example.adplatform.infra.redis.budget;

import com.example.adplatform.admin.entity.PlanEntity;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

/**
 * 广告计划预算的 Redis 访问服务，封装预算粗过滤、原子扣费和缓存重建。
 */
public interface BudgetRedisService {

    /**
     * 召回阶段预算粗过滤：预算还有余额才允许计划继续参与召回。
     *
     * @param plan 待判断的广告计划
     * @param statDate 预算统计日期
     * @return 单日预算和总预算都未用尽时返回 {@code true}
     */
    boolean hasAvailableBudget(PlanEntity plan, LocalDate statDate);

    /**
     * 批量找出预算无效或已经耗尽的计划，在线投放用它避免逐计划 Redis 往返。
     *
     * @param plans 候选计划集合
     * @param statDate 单日预算统计日期
     * @return 不允许继续投放的计划 ID
     */
    Set<Long> findUnavailablePlans(Collection<PlanEntity> plans, LocalDate statDate);

    /**
     * 扣费阶段实时预算扣减：预算足够时原子累加已消耗金额。
     *
     * @param eventId 事件唯一标识，用于保证重试不重复扣减预算
     * @param plan 待扣费的广告计划
     * @param statDate 预算统计日期
     * @param amount 本次扣费金额
     * @return 预算足够且扣减成功时返回 {@code true}
     */
    boolean tryChargeOnce(String eventId, PlanEntity plan, LocalDate statDate, long amount);

    /** 事件消费链路使用计划快照直接扣费，避免为了传参构造 PlanEntity。 */
    boolean tryChargeOnce(
            String eventId,
            Long planId,
            Long budgetDaily,
            Long budgetTotal,
            LocalDate statDate,
            long amount);

    /**
     * 根据 charge_record 成功扣费流水重建指定计划的预算消耗缓存。
     *
     * @param plan 待重建预算的广告计划
     * @param statDate 单日预算的统计日期
     */
    void rebuildBudget(PlanEntity plan, LocalDate statDate);
}
