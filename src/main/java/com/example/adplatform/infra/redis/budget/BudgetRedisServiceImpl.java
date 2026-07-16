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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 基于 Redis 和扣费流水表实现的广告计划预算服务。
 *
 * <p>正常情况下使用 Lua 脚本原子检查并累加单日、总预算消耗；Redis 异常时回源
 * {@code charge_record} 提供保守判断。</p>
 */
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

    /** {@inheritDoc} */
    @Override
    public boolean hasAvailableBudget(PlanEntity plan, LocalDate statDate) {
        if (!hasValidBudget(plan)) {
            return false;
        }
        BudgetCost cost = getBudgetCost(plan, statDate);
        return cost.dailyCost() < plan.getBudgetDaily() && cost.totalCost() < plan.getBudgetTotal();
    }

    /** {@inheritDoc} */
    @Override
    public Set<Long> findUnavailablePlans(Collection<PlanEntity> plans, LocalDate statDate) {
        if (plans == null || plans.isEmpty()) {
            return Set.of();
        }
        List<PlanEntity> uniquePlans = plans.stream()
                .filter(this::hasValidBudget)
                .collect(java.util.stream.Collectors.toMap(
                        PlanEntity::getId,
                        plan -> plan,
                        (first, ignored) -> first,
                        java.util.LinkedHashMap::new))
                .values().stream().toList();
        Set<Long> unavailable = new HashSet<>();
        plans.stream().filter(plan -> !hasValidBudget(plan)).forEach(plan -> {
            if (plan != null && plan.getId() != null) unavailable.add(plan.getId());
        });
        List<String> keys = new ArrayList<>(uniquePlans.size() * 2);
        uniquePlans.forEach(plan -> {
            keys.add(dailyBudgetKey(statDate, plan.getId()));
            keys.add(totalBudgetKey(plan.getId()));
        });
        try {
            List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
            for (int i = 0; i < uniquePlans.size(); i++) {
                PlanEntity plan = uniquePlans.get(i);
                String daily = values == null ? null : values.get(i * 2);
                String total = values == null ? null : values.get(i * 2 + 1);
                if (daily == null || total == null) {
                    if (!hasAvailableBudget(plan, statDate)) unavailable.add(plan.getId());
                } else if (Long.parseLong(daily) >= plan.getBudgetDaily()
                        || Long.parseLong(total) >= plan.getBudgetTotal()) {
                    unavailable.add(plan.getId());
                }
            }
        } catch (RuntimeException ex) {
            uniquePlans.forEach(plan -> {
                if (!hasAvailableBudget(plan, statDate)) unavailable.add(plan.getId());
            });
        }
        return unavailable;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /**
     * 读取指定计划的单日和总预算消耗，缓存缺失或异常时回源数据库。
     *
     * @param plan 广告计划
     * @param statDate 统计日期
     * @return 预算消耗快照
     */
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

    /**
     * 在扣费前确保单日和总预算 Key 均已存在；缺失时从数据库重建。
     *
     * @param plan 广告计划
     * @param statDate 统计日期
     */
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

    /**
     * 从成功扣费流水聚合指定计划的单日和累计消耗。
     *
     * @param planId 广告计划标识
     * @param statDate 统计日期
     * @return 数据库中的预算消耗快照
     */
    private BudgetCost loadBudgetCostFromDatabase(Long planId, LocalDate statDate) {
        return new BudgetCost(
                chargeRecordMapper.sumSuccessAmountByPlanOnDate(planId, statDate),
                chargeRecordMapper.sumSuccessAmountByPlan(planId));
    }

    /**
     * 校验计划是否具备可用的单日和总预算配置。
     *
     * @param plan 待校验的广告计划
     * @return 计划标识和两类预算都有效时返回 {@code true}
     */
    private boolean hasValidBudget(PlanEntity plan) {
        return plan != null
                && plan.getId() != null
                && plan.getBudgetDaily() != null
                && plan.getBudgetTotal() != null
                && plan.getBudgetDaily() > 0
                && plan.getBudgetTotal() > 0;
    }

    /**
     * @param statDate 统计日期
     * @param planId 广告计划标识
     * @return 单日预算消耗 Key
     */
    private String dailyBudgetKey(LocalDate statDate, Long planId) {
        return RedisKeyConstants.planDailyBudget(statDate, planId);
    }

    /**
     * @param planId 广告计划标识
     * @return 累计预算消耗 Key
     */
    private String totalBudgetKey(Long planId) {
        return RedisKeyConstants.planTotalBudget(planId);
    }

    /**
     * 单日和累计预算消耗快照。
     *
     * @param dailyCost 当日已消耗金额
     * @param totalCost 计划累计已消耗金额
     */
    private record BudgetCost(long dailyCost, long totalCost) {
    }
}
