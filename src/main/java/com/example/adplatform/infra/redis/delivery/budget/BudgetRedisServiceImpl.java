package com.example.adplatform.infra.redis.delivery.budget;

import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.delivery.port.BudgetAvailabilityPort;
import com.example.adplatform.infra.redis.delivery.DeliveryRedisKeys;
import com.example.adplatform.tracking.mapper.ChargeRecordMapper;
import com.example.adplatform.tracking.port.BudgetChargePort;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * 基于 Redis 和扣费流水表实现的广告计划预算服务。
 *
 * <p>正常情况下使用 Lua 脚本原子检查并累加单日、总预算消耗；Redis 异常时回源
 * {@code charge_record} 提供保守判断。</p>
 */
@RequiredArgsConstructor
@Service
public class BudgetRedisServiceImpl implements BudgetAvailabilityPort, BudgetChargePort {

    private static final Logger log = LoggerFactory.getLogger(BudgetRedisServiceImpl.class);
    private static final Duration DAILY_BUDGET_TTL = Duration.ofDays(2);
    private static final Duration TOTAL_BUDGET_TTL = Duration.ofDays(30);
    private static final Duration EVENT_CHARGE_DECISION_TTL = Duration.ofDays(14);
    //todo:这里是不是太简单了，previousDecision的值只有 0/1/null，考虑关于扣费时间和召回时间，以及扣费期间计费遭到修改
    private static final RedisScript<Long> TRY_CHARGE_SCRIPT = RedisScript.of(
            new ClassPathResource("redis/scripts/try-charge.lua"),
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ChargeRecordMapper chargeRecordMapper;

    /**
     * {@inheritDoc}
     * 召回阶段预算粗过滤：预算还有余额才允许计划继续参与召回。
     *
     * @param plan 待判断的广告计划
     * @param statDate 预算统计日期
     * @return 单日预算和总预算都未用尽时返回 {@code true}
     */
    public boolean hasAvailableBudget(PlanEntity plan, LocalDate statDate) {
        if (!hasValidBudget(plan)) {
            return false;
        }
        BudgetCost cost = getBudgetCost(plan, statDate);
        return cost.dailyCost() < plan.getBudgetDaily() && cost.totalCost() < plan.getBudgetTotal();
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
            String totalCost = stringRedisTemplate.opsForValue().get(totalBudgetKey(plan.getId()));//这里回源之前再次查询redis，意义不大，todo:后续删除，直接回源/数据库有可能在此时重建？
            if (dailyCost != null && totalCost != null) {//todo:另外，牵扯到redis和数据库数据同步问题是不是都要考虑条带锁 布隆过滤器 防误杀 那一套？
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
    /** {@inheritDoc} */
    @Override
    public Set<Long> findUnavailablePlans(Collection<PlanEntity> plans, LocalDate statDate) {
        if (plans == null || plans.isEmpty()) {
            return Set.of();
        }
        List<PlanEntity> uniquePlans = plans.stream()
                .filter(this::hasValidBudget)//筛选掉没有预算的计划，todo:没有预算就不校验？，后续加入补偿机制，或者强制有预算/默认值
                .collect(Collectors.toMap(
                        PlanEntity::getId,
                        plan -> plan,
                        (first, ignored) -> first,//再次去重，不信任任何调用方
                        LinkedHashMap::new))//保持顺序
                .values().stream().toList();
        Set<Long> unavailable = new HashSet<>();
        plans.stream().filter(plan -> !hasValidBudget(plan)).forEach(plan -> {
            if (plan != null && plan.getId() != null) unavailable.add(plan.getId());//把无预算的加入不可用集合，与后续预算不足合并成并集
        });
        List<String> keys = new ArrayList<>(uniquePlans.size() * 2);
        uniquePlans.forEach(plan -> {//把有预算的PlanId取出来加入集合拼接成key用于后续批量进入Redis
            keys.add(dailyBudgetKey(statDate, plan.getId()));
            keys.add(totalBudgetKey(plan.getId()));
        });
        try {
            List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);//批量发送一次请求，减少网络开销
            for (int i = 0; i < uniquePlans.size(); i++) {
                PlanEntity plan = uniquePlans.get(i);
                String daily = values == null ? null : values.get(i * 2);//得到当天消耗
                String total = values == null ? null : values.get(i * 2 + 1);//得到累积消耗
                if (daily == null || total == null) {
                    if (!hasAvailableBudget(plan, statDate)) unavailable.add(plan.getId());//降级回源策略（todo:后续优化，考虑这些数据是否可以常驻内存，内存溢出怎么办?）
                } else if (Long.parseLong(daily) >= plan.getBudgetDaily()//查询正常直接比较
                        || Long.parseLong(total) >= plan.getBudgetTotal()) {
                    unavailable.add(plan.getId());
                }
            }
        } catch (RuntimeException ex) {
            uniquePlans.forEach(plan -> {
                if (!hasAvailableBudget(plan, statDate)) unavailable.add(plan.getId());
            });
        }
        return unavailable;//最终返回不可用列表
    }

    /**
     * {@inheritDoc}
     * 扣费阶段实时预算扣减：预算足够时原子累加已消耗金额。
     *
     * @param eventId 事件唯一标识，用于保证重试不重复扣减预算
     * @param plan 待扣费的广告计划
     * @param statDate 预算统计日期
     * @param amount 本次扣费金额
     * @return 预算足够且扣减成功时返回 {@code true}
     */
    public boolean tryChargeOnce(String eventId, PlanEntity plan, LocalDate statDate, long amount) {
        if (!hasValidBudget(plan)) {
            return false;
        }
        return tryChargeOnce(
                eventId,
                plan.getId(),
                plan.getBudgetDaily(),
                plan.getBudgetTotal(),
                statDate,
                amount);
    }

    @Override
    public boolean tryChargeOnce(
            String eventId,
            Long planId,
            Long budgetDaily,
            Long budgetTotal,
            LocalDate statDate,
            long amount) {
        if (amount <= 0) {
            return false;
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId不能为空");
        }
        if (planId == null || budgetDaily == null || budgetTotal == null
                || budgetDaily <= 0 || budgetTotal <= 0) {
            return false;
        }
        ensureBudgetKeys(planId, statDate);

        try {
            Long result = stringRedisTemplate.execute(
                    TRY_CHARGE_SCRIPT,
                    List.of(
                            dailyBudgetKey(statDate, planId),
                            totalBudgetKey(planId),
                            DeliveryRedisKeys.eventChargeDecision(eventId)),
                    String.valueOf(amount),
                    String.valueOf(budgetDaily),
                    String.valueOf(budgetTotal),
                    String.valueOf(DAILY_BUDGET_TTL.toSeconds()),
                    String.valueOf(TOTAL_BUDGET_TTL.toSeconds()),
                    String.valueOf(EVENT_CHARGE_DECISION_TTL.toSeconds()));
            return result != null && result == 1L;
        } catch (RuntimeException ex) {
            log.warn("Redis 预算扣减失败，planId={}，本次降级查询 charge_record：{}", planId, ex.getMessage());
            BudgetCost cost = loadBudgetCostFromDatabase(planId, statDate);
            return cost.dailyCost() + amount <= budgetDaily
                    && cost.totalCost() + amount <= budgetTotal;
        }
    }

    /**
     * {@inheritDoc}
     * 根据 charge_record 成功扣费流水重建指定计划的预算消耗缓存。
     *
     * @param plan 待重建预算的广告计划
     * @param statDate 单日预算的统计日期
     */
    public void rebuildBudget(PlanEntity plan, LocalDate statDate) {//根据recordCharge表预热Redis，写入当日消耗和总消耗，todo:为什么不写入预算然后扣到负数阻止？
        if (plan == null || plan.getId() == null) {
            return;
        }//非空校验无效直接返回
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
     * 在扣费前确保单日和总预算 Key 均已存在；缺失时从数据库重建。
     *
     * @param planId 广告计划
     * @param statDate 统计日期
     */
    private void ensureBudgetKeys(Long planId, LocalDate statDate) {
        try {
            Boolean hasDailyKey = stringRedisTemplate.hasKey(dailyBudgetKey(statDate, planId));
            Boolean hasTotalKey = stringRedisTemplate.hasKey(totalBudgetKey(planId));
            if (Boolean.TRUE.equals(hasDailyKey) && Boolean.TRUE.equals(hasTotalKey)) {//todo:这是什么鬼？
                return;
            }
        } catch (RuntimeException ex) {
            return;
        }
        rebuildBudget(planId, statDate);//todo:有无重试机制(在后续的lua脚本里面有的)
    }

    private void rebuildBudget(Long planId, LocalDate statDate) {
        BudgetCost cost = loadBudgetCostFromDatabase(planId, statDate);
        try {
            stringRedisTemplate.opsForValue().set(
                    dailyBudgetKey(statDate, planId), String.valueOf(cost.dailyCost()), DAILY_BUDGET_TTL);
            stringRedisTemplate.opsForValue().set(
                    totalBudgetKey(planId), String.valueOf(cost.totalCost()), TOTAL_BUDGET_TTL);
        } catch (RuntimeException ex) {
            log.warn("重建 Redis 预算缓存失败，planId={}：{}", planId, ex.getMessage());
        }
    }

    /**
     * 从成功扣费流水聚合指定计划的单日和累计消耗。
     *
     * @param planId 广告计划标识
     * @param statDate 统计日期
     * @return 数据库中的预算消耗快照
     */
    private BudgetCost loadBudgetCostFromDatabase(Long planId, LocalDate statDate) {//从chargeRecord表聚合读取当日消耗和总消耗
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
        return DeliveryRedisKeys.planDailyBudget(statDate, planId);
    }

    /**
     * @param planId 广告计划标识
     * @return 累计预算消耗 Key
     */
    private String totalBudgetKey(Long planId) {
        return DeliveryRedisKeys.planTotalBudget(planId);
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
