package com.example.adplatform.infra.redis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.PlanStatus;
import com.example.adplatform.admin.mapper.PlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Component
public class BudgetWarmUpRunner implements ApplicationRunner {

    private final PlanMapper planMapper;
    private final BudgetRedisService budgetRedisService;

    @Override
    public void run(ApplicationArguments args) {
        List<PlanEntity> onlinePlans = planMapper.selectList(new LambdaQueryWrapper<PlanEntity>()
                .eq(PlanEntity::getStatus, PlanStatus.ONLINE.name()));
        LocalDate today = LocalDate.now();
        onlinePlans.forEach(plan -> budgetRedisService.rebuildBudget(plan, today));
    }
}
