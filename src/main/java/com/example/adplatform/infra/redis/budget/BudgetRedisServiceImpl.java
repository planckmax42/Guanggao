package com.example.adplatform.infra.redis.budget;

import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.infra.redis.RedisKeyConstants;
import com.example.adplatform.tracking.mapper.ChargeRecordMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Service
public class BudgetRedisServiceImpl implements BudgetRedisService {

    private static final Logger log = LoggerFactory.getLogger(BudgetRedisServiceImpl.class);
    private static final Duration DAILY_BUDGET_TTL = Duration.ofDays(2);
    private static final Duration TOTAL_BUDGET_TTL = Duration.ofDays(30);

    private static final DefaultRedisScript<Long> TRY_CHARGE_SCRIPT = new DefaultRedisScript<>("""
            local dailyKey = KEYS[1]
            local totalKey = KEYS[2]
            local amount = tonumber(ARGV[1])
            local dailyBudget = tonumber(ARGV[2])
            local totalBudget = tonumber(ARGV[3])
            local dailyTtl = tonumber(ARGV[4])
            local totalTtl = tonumber(ARGV[5])

            local dailyCost = tonumber(redis.call('GET', dailyKey) or '0')
            local totalCost = tonumber(redis.call('GET', totalKey) or '0')

            if dailyCost + amount > dailyBudget then
                return 0
            end
            if totalCost + amount > totalBudget then
                return 0
            end

            redis.call('INCRBY', dailyKey, amount)
            redis.call('INCRBY', totalKey, amount)
            redis.call('EXPIRE', dailyKey, dailyTtl)
            redis.call('EXPIRE', totalKey, totalTtl)
            return 1
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ChargeRecordMapper chargeRecordMapper;

    @Override
    public boolean hasAvailableBudget(PlanEntity plan, LocalDate statDate) {
        if (!hasValidBudget(plan)) {
            return false;
        }
        BudgetCost cost = getBudgetCost(plan, statDate);
        return cost.dailyCost() < plan.getBudgetDaily() && cost.totalCost() < plan.getBudgetTotal();
    }

    @Override
    public boolean tryCharge(PlanEntity plan, LocalDate statDate, long amount) {
        if (amount <= 0) {
            return false;
        }
        if (!hasValidBudget(plan)) {
            return false;
        }
        ensureBudgetKeys(plan, statDate);

        try {
            Long result = stringRedisTemplate.execute(
                    TRY_CHARGE_SCRIPT,
                    List.of(dailyBudgetKey(statDate, plan.getId()), totalBudgetKey(plan.getId())),
                    String.valueOf(amount),
                    String.valueOf(plan.getBudgetDaily()),
                    String.valueOf(plan.getBudgetTotal()),
                    String.valueOf(DAILY_BUDGET_TTL.toSeconds()),
                    String.valueOf(TOTAL_BUDGET_TTL.toSeconds()));
            return result != null && result == 1L;
        } catch (RuntimeException ex) {
            log.warn("Redis 预算扣减失败，planId={}，本次降级查询 charge_record：{}", plan.getId(), ex.getMessage());
            BudgetCost cost = loadBudgetCostFromDatabase(plan.getId(), statDate);
            return cost.dailyCost() + amount <= plan.getBudgetDaily()
                    && cost.totalCost() + amount <= plan.getBudgetTotal();
        }
    }

    @Override
    public void rebuildBudget(PlanEntity plan, LocalDate statDate) {
        if (plan == null || plan.getId() == null) {
            return;
        }
        BudgetCost cost = loadBudgetCostFromDatabase(plan.getId(), statDate);
        try {
            stringRedisTemplate.opsForValue().set(
                    dailyBudgetKey(statDate, plan.getId()),
                    String.valueOf(cost.dailyCost()),
                    DAILY_BUDGET_TTL);
            stringRedisTemplate.opsForValue().set(
                    totalBudgetKey(plan.getId()),
                    String.valueOf(cost.totalCost()),
                    TOTAL_BUDGET_TTL);
        } catch (RuntimeException ex) {
            log.warn("重建 Redis 预算缓存失败，planId={}：{}", plan.getId(), ex.getMessage());
        }
    }

    private BudgetCost getBudgetCost(PlanEntity plan, LocalDate statDate) {
        try {
            String dailyCost = stringRedisTemplate.opsForValue().get(dailyBudgetKey(statDate, plan.getId()));
            String totalCost = stringRedisTemplate.opsForValue().get(totalBudgetKey(plan.getId()));
            if (dailyCost != null && totalCost != null) {
                return new BudgetCost(Long.parseLong(dailyCost), Long.parseLong(totalCost));
            }
        } catch (RuntimeException ex) {
            log.warn("读取 Redis 预算缓存失败，planId={}，本次降级查询 charge_record：{}", plan.getId(), ex.getMessage());
            return loadBudgetCostFromDatabase(plan.getId(), statDate);
        }

        BudgetCost cost = loadBudgetCostFromDatabase(plan.getId(), statDate);
        rebuildBudget(plan, statDate);
        return cost;
    }

    private void ensureBudgetKeys(PlanEntity plan, LocalDate statDate) {
        try {
            Boolean hasDailyKey = stringRedisTemplate.hasKey(dailyBudgetKey(statDate, plan.getId()));
            Boolean hasTotalKey = stringRedisTemplate.hasKey(totalBudgetKey(plan.getId()));
            if (Boolean.TRUE.equals(hasDailyKey) && Boolean.TRUE.equals(hasTotalKey)) {
                return;
            }
        } catch (RuntimeException ex) {
            return;
        }
        rebuildBudget(plan, statDate);
    }

    private BudgetCost loadBudgetCostFromDatabase(Long planId, LocalDate statDate) {
        return new BudgetCost(
                chargeRecordMapper.sumSuccessAmountByPlanOnDate(planId, statDate),
                chargeRecordMapper.sumSuccessAmountByPlan(planId));
    }

    private boolean hasValidBudget(PlanEntity plan) {
        return plan != null
                && plan.getId() != null
                && plan.getBudgetDaily() != null
                && plan.getBudgetTotal() != null
                && plan.getBudgetDaily() > 0
                && plan.getBudgetTotal() > 0;
    }

    private String dailyBudgetKey(LocalDate statDate, Long planId) {
        return RedisKeyConstants.planDailyBudget(statDate, planId);
    }

    private String totalBudgetKey(Long planId) {
        return RedisKeyConstants.planTotalBudget(planId);
    }

    private record BudgetCost(long dailyCost, long totalCost) {
    }
}
