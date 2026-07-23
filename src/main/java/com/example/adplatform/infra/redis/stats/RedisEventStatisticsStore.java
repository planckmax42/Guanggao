package com.example.adplatform.infra.redis.stats;

import com.example.adplatform.infra.redis.RedisKeyConstants;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventMaterialMetadata;
import com.example.adplatform.tracking.service.EventStatisticsStore;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/** 使用 Lua 把事件去重标记、频控和日报增量作为一个 Redis 原子操作写入。 */
@RequiredArgsConstructor
@Service
public class RedisEventStatisticsStore implements EventStatisticsStore {

    private static final Duration DEDUP_TTL = Duration.ofDays(14);
    private static final Duration STATS_TTL = Duration.ofDays(3);
    private static final Duration FREQUENCY_TTL = Duration.ofDays(2);

    private static final RedisScript<Long> RECORD_EVENT_SCRIPT = RedisScript.of(
            new ClassPathResource("redis/scripts/record-event.lua"),
            Long.class);

    private static final RedisScript<Long> RECORD_COST_SCRIPT = RedisScript.of(
            new ClassPathResource("redis/scripts/record-cost.lua"),
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean recordEventOnce(EventMessage message, EventMaterialMetadata metadata) {
        long impressions = message.eventType() == EventType.IMPRESSION ? 1L : 0L;
        long clicks = message.eventType() == EventType.CLICK ? 1L : 0L;
        long conversions = message.eventType() == EventType.CONVERSION ? 1L : 0L;
        LocalDate localDate = message.eventTime().toLocalDate();
        String statsKey = statsKey(message, metadata, localDate);
        Long result = stringRedisTemplate.execute(
                RECORD_EVENT_SCRIPT,
                List.of(
                        RedisKeyConstants.eventStatisticsProcessed(message.eventId()),//事件去重第一步
                        statsKey,
                        RedisKeyConstants.dailyStatsDirtySet(localDate),
                        RedisKeyConstants.viewerPlanFrequency(
                                message.viewerId(), metadata.planId(), localDate)),
                String.valueOf(DEDUP_TTL.toSeconds()),
                String.valueOf(impressions),
                String.valueOf(clicks),
                String.valueOf(conversions),
                String.valueOf(STATS_TTL.toSeconds()),
                String.valueOf(FREQUENCY_TTL.toSeconds()));
        return result != null && result == 1L;
    }

    @Override
    public boolean recordCostOnce(
            EventMessage message,
            EventMaterialMetadata metadata,
            long costAmount) {
        LocalDate statDate = message.eventTime().toLocalDate();
        Long result = stringRedisTemplate.execute(
                RECORD_COST_SCRIPT,
                List.of(
                        RedisKeyConstants.eventCostStatisticsProcessed(message.eventId()),
                        statsKey(message, metadata, statDate),
                        RedisKeyConstants.dailyStatsDirtySet(statDate)),
                String.valueOf(DEDUP_TTL.toSeconds()),
                String.valueOf(costAmount),
                String.valueOf(STATS_TTL.toSeconds()));
        return result != null && result == 1L;
    }

    private String statsKey(
            EventMessage message,
            EventMaterialMetadata metadata,
            LocalDate statDate) {
        return RedisKeyConstants.dailyStats(
                statDate,
                metadata.planId(),
                message.materialId(),
                metadata.slotId());
    }
}
