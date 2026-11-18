package com.example.adplatform.infra.redis.delivery.budget.budgetMQ;

import com.example.adplatform.delivery.portMQ.DeliveryBudgetControlPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Redis 预算快照，用于投放初筛，不执行实际扣费。 */
@Service
@RequiredArgsConstructor
public class BudgetControlService implements DeliveryBudgetControlPort {

    private static final long TTL_SECONDS = Duration.ofDays(2).toSeconds();
    private static final List<String> FIELDS = List.of(
            "daily_limit", "daily_spent", "total_limit", "total_spent");

    private static final RedisScript<Long> RECORD_SNAPSHOT_SCRIPT = RedisScript.of("""
            redis.call('HSET', KEYS[1],
                'daily_limit', ARGV[1], 'daily_spent', ARGV[2],
                'total_limit', ARGV[3], 'total_spent', ARGV[4])
            redis.call('EXPIRE', KEYS[1], ARGV[5])
            return 1
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 写入完整预算快照并刷新两天 TTL，金额使用统一的整数货币单位。
     * totalSpent 为截至本次快照的累计消耗，不是当日消耗。
     * 调用方应在账务提交后按顺序同步最新快照；此方法不处理消息乱序。
     */
    public void recordSnapshot(
            Long planId, LocalDate statDate,
            long dailyLimit, long dailySpent, long totalLimit, long totalSpent) {
        stringRedisTemplate.execute(
                RECORD_SNAPSHOT_SCRIPT,
                List.of(buildKey(planId, statDate)),
                String.valueOf(dailyLimit), String.valueOf(dailySpent),
                String.valueOf(totalLimit), String.valueOf(totalSpent),
                String.valueOf(TTL_SECONDS));
    }

    /**
     * 返回日预算和总预算都未耗尽的计划 ID，保留输入顺序并去重。
     * 每个计划通过 HMGET 读取四个字段；快照缺失时不放行，Redis 异常向上抛出。
     */
    public Set<Long> findAllowedPlans(Collection<Long> planIds, LocalDate statDate) {
        Set<Long> allowed = new LinkedHashSet<>();
        for (Long planId : new LinkedHashSet<>(planIds)) {
            List<String> values = stringRedisTemplate.<String, String>opsForHash()
                    .multiGet(buildKey(planId, statDate), FIELDS);
            if (values.contains(null)) {
                continue;
            }
            long dailyLimit = Long.parseLong(values.get(0));
            long dailySpent = Long.parseLong(values.get(1));
            long totalLimit = Long.parseLong(values.get(2));
            long totalSpent = Long.parseLong(values.get(3));
            if (dailySpent < dailyLimit && totalSpent < totalLimit) {
                allowed.add(planId);
            }
        }
        return allowed;
    }

    private String buildKey(Long planId, LocalDate statDate) {
        return "budget:snapshot:" + planId + ":" + statDate;
    }
}
