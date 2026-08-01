package com.example.adplatform.infra.warmup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.PlanStatus;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.infra.redis.delivery.budget.BudgetRedisServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** 从 MySQL 加载在线计划并预热当日预算缓存。 */
@Component
@RequiredArgsConstructor
public class BudgetWarmUpTask {

    private final PlanMapper planMapper;
    private final BudgetRedisServiceImpl budgetRedisService;

    public void warmUp() {
        List<PlanEntity> onlinePlans = planMapper.selectList(new LambdaQueryWrapper<PlanEntity>()
                .eq(PlanEntity::getStatus, PlanStatus.ONLINE.name()));
        LocalDate today = LocalDate.now();
        onlinePlans.forEach(plan -> budgetRedisService.rebuildBudget(plan, today));
    }
}
