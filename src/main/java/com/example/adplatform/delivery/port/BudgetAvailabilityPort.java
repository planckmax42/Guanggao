package com.example.adplatform.delivery.port;

import com.example.adplatform.admin.entity.PlanEntity;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

/**
 * 广告计划预算的 Redis 访问服务，封装预算粗过滤、原子扣费和缓存重建。
 */
public interface BudgetAvailabilityPort {

    /**
     * 批量找出预算无效或已经耗尽的计划，在线投放用它避免逐计划 Redis 往返。
     *
     * @param plans 候选计划集合
     * @param statDate 单日预算统计日期
     * @return 不允许继续投放的计划 ID
     */
    Set<Long> findUnavailablePlans(Collection<PlanEntity> plans, LocalDate statDate);
}
