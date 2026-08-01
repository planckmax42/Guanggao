package com.example.adplatform.tracking.port;

import java.time.LocalDate;

/** 事件计费链路执行幂等预算扣减的端口。 */
public interface BudgetChargePort {

    /** 事件消费链路使用计划快照直接扣费，避免为了传参构造 PlanEntity。 */
    boolean tryChargeOnce(
            String eventId,
            Long planId,
            Long budgetDaily,
            Long budgetTotal,
            LocalDate statDate,
            long amount);
}
