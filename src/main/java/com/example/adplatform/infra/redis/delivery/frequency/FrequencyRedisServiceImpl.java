package com.example.adplatform.infra.redis.delivery.frequency;

import com.example.adplatform.delivery.port.FrequencyControlPort;
import com.example.adplatform.infra.redis.delivery.DeliveryRedisKeys;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 Redis 计数器实现的每日用户曝光频控服务。
 *
 * <p>Redis 不可用或缓存数据格式异常时采用放行策略，避免频控故障中断广告投放。</p>
 */
@RequiredArgsConstructor
@Service
public class FrequencyRedisServiceImpl implements FrequencyControlPort {

    private static final Logger log = LoggerFactory.getLogger(FrequencyRedisServiceImpl.class);
    private static final Duration FREQUENCY_TTL = Duration.ofDays(2);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * {@inheritDoc}
     * 判断同一用户当天看到同一计划的次数是否已经达到上限。
     *
     * @param viewerId 用户标识
     * @param planId 广告计划标识
     * @param statDate 统计日期
     * @param maxFrequency 允许的最大曝光次数
     * @return 当前次数已达上限时返回 {@code true}
     */
    public boolean isViewerPlanFrequencyExceeded(Long viewerId, Long planId, LocalDate statDate, int maxFrequency) {
        if (viewerId == null || planId == null || maxFrequency <= 0) {
            return false;
        }
        String key = DeliveryRedisKeys.viewerPlanFrequency(viewerId, planId, statDate);
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            long current = value == null ? 0L : Long.parseLong(value);
            return current >= maxFrequency;
        } catch (RuntimeException ex) {
            log.warn("读取用户频控缓存失败，viewerId={}，planId={}，本次投放放行：{}", viewerId, planId, ex.getMessage());
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public Set<Long> findExceededPlans(
            Long viewerId,
            Collection<Long> planIds,
            LocalDate statDate,
            int maxFrequency) {
        if (viewerId == null || planIds == null || planIds.isEmpty() || maxFrequency <= 0) {
            return Set.of();
        }//不相信任何调用方，传入后执行一次参数校验
        List<Long> uniquePlanIds = planIds.stream().distinct().toList();//再次去重PlanId
        List<String> keys = uniquePlanIds.stream()
                .map(planId -> DeliveryRedisKeys.viewerPlanFrequency(viewerId, planId, statDate))
                .toList();//批量转换成key，便于后续打包查询，减少网络开销
        try {
            List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
            Set<Long> exceeded = new HashSet<>();
            for (int i = 0; i < uniquePlanIds.size(); i++) {
                String value = values == null ? null : values.get(i);
                if (value != null && Long.parseLong(value) >= maxFrequency) {
                    exceeded.add(uniquePlanIds.get(i));//超频后加入不可用列表，todo:后续是否可以单独本地保存一个不可用列表，进一步减少网络开销
                }
            }
            return exceeded;
        } catch (RuntimeException ex) {
            log.warn("批量读取用户频控失败，viewerId={}，本次投放放行", viewerId, ex);
            return Set.of();
        }
    }

    /**
     * {@inheritDoc}
     * 曝光事件消费成功后，累加同一用户当天看到同一计划的次数。
     *
     * @param viewerId 用户标识
     * @param planId 广告计划标识
     * @param statDate 统计日期
     */
    public void incrementViewerPlanImpression(Long viewerId, Long planId, LocalDate statDate) {
        if (viewerId == null || planId == null) {
            return;
        }
        String key = DeliveryRedisKeys.viewerPlanFrequency(viewerId, planId, statDate);
        try {
            stringRedisTemplate.opsForValue().increment(key);
            stringRedisTemplate.expire(key, FREQUENCY_TTL);
        } catch (RuntimeException ex) {
            log.warn("写入用户频控缓存失败，viewerId={}，planId={}：{}", viewerId, planId, ex.getMessage());
        }
    }
}
