package com.example.adplatform.infra.redis.budget;

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

/**
 * 应用启动后的预算缓存预热任务，从 MySQL 加载在线计划并重建当日预算消耗。
 */
@RequiredArgsConstructor
@Component
public class BudgetWarmUpRunner implements ApplicationRunner {

    private final PlanMapper planMapper;
    private final BudgetRedisService budgetRedisService;

    /**
     * 在 Spring Boot 启动完成后重建所有在线计划的预算缓存。
     *
     * @param args 应用启动参数，本任务不使用
     */
    @Override
    public void run(ApplicationArguments args) {//开机预热Redis,给Online计划写入当日消耗和总消耗，用于后续预算控制
        List<PlanEntity> onlinePlans = planMapper.selectList(new LambdaQueryWrapper<PlanEntity>()
                .eq(PlanEntity::getStatus, PlanStatus.ONLINE.name()));
        LocalDate today = LocalDate.now();
        onlinePlans.forEach(plan -> budgetRedisService.rebuildBudget(plan, today));
    }
}
