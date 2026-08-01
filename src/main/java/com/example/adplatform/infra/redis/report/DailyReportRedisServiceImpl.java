package com.example.adplatform.infra.redis.report;

import com.example.adplatform.report.mapper.DailyReportMapper;
import com.example.adplatform.report.port.DailyReportAccumulatorPort;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class DailyReportRedisServiceImpl implements DailyReportAccumulatorPort {

    private static final String IMPRESSION_COUNT = "impression_count";
    private static final String CLICK_COUNT = "click_count";
    private static final String CONVERSION_COUNT = "conversion_count";
    private static final String COST_AMOUNT = "cost_amount";
    private static final Duration STATS_TTL = Duration.ofDays(3);
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * 原子取出当前累计值，并从 Redis 中扣减这批快照值。
     * 这样刷库期间如果有新的事件写入，不会被后续清理误删。
     */
    private static final RedisScript<List> POP_STATS_SCRIPT = RedisScript.of(
            new ClassPathResource("redis/scripts/pop-daily-stats.lua"),
            List.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final DailyReportMapper dailyReportMapper;

    @Override
    public void incrementDailyReport(
            LocalDate statDate,
            Long planId,
            Long materialId,
            Long slotId,
            long impressionCount,
            long clickCount,
            long conversionCount,
            long costAmount) {
        String statsKey = ReportRedisKeys.dailyStats(statDate, planId, materialId, slotId);
        String dirtyKey = ReportRedisKeys.dailyStatsDirtySet(statDate);

        incrementHashField(statsKey, IMPRESSION_COUNT, impressionCount);
        incrementHashField(statsKey, CLICK_COUNT, clickCount);
        incrementHashField(statsKey, CONVERSION_COUNT, conversionCount);
        incrementHashField(statsKey, COST_AMOUNT, costAmount);
        stringRedisTemplate.opsForSet().add(dirtyKey, statsKey);
        stringRedisTemplate.expire(statsKey, STATS_TTL);
        stringRedisTemplate.expire(dirtyKey, STATS_TTL);
    }

    @Override
    public void flushDailyReport(LocalDate statDate) {
        String dirtyKey = ReportRedisKeys.dailyStatsDirtySet(statDate);
        Set<String> statsKeys = stringRedisTemplate.opsForSet().members(dirtyKey);
        if (statsKeys == null || statsKeys.isEmpty()) {
            return;
        }

        for (String statsKey : statsKeys) {
            StatsKeyParts keyParts = parseStatsKey(statsKey);
            if (keyParts == null) {
                stringRedisTemplate.opsForSet().remove(dirtyKey, statsKey);
                continue;
            }

            StatsSnapshot snapshot = popSnapshot(statsKey, dirtyKey);
            if (snapshot.isEmpty()) {
                continue;
            }

            try {
                dailyReportMapper.upsertIncrement(
                        keyParts.statDate(),
                        keyParts.planId(),
                        keyParts.materialId(),
                        keyParts.slotId(),
                        snapshot.impressionCount(),
                        snapshot.clickCount(),
                        snapshot.conversionCount(),
                        snapshot.costAmount());
            } catch (RuntimeException ex) {
                restoreSnapshot(statsKey, dirtyKey, snapshot);
                throw ex;
            }
        }
    }

    private void incrementHashField(String key, String field, long delta) {
        if (delta != 0) {
            stringRedisTemplate.opsForHash().increment(key, field, delta);
        }
    }

    private StatsSnapshot popSnapshot(String statsKey, String dirtyKey) {
        List<?> values = stringRedisTemplate.execute(
                POP_STATS_SCRIPT,
                List.of(statsKey, dirtyKey),
                IMPRESSION_COUNT,
                CLICK_COUNT,
                CONVERSION_COUNT,
                COST_AMOUNT);
        if (values == null || values.size() < 4) {
            return new StatsSnapshot(0L, 0L, 0L, 0L);
        }
        return new StatsSnapshot(
                toLong(values.get(0)),
                toLong(values.get(1)),
                toLong(values.get(2)),
                toLong(values.get(3)));
    }

    private void restoreSnapshot(String statsKey, String dirtyKey, StatsSnapshot snapshot) {
        incrementHashField(statsKey, IMPRESSION_COUNT, snapshot.impressionCount());
        incrementHashField(statsKey, CLICK_COUNT, snapshot.clickCount());
        incrementHashField(statsKey, CONVERSION_COUNT, snapshot.conversionCount());
        incrementHashField(statsKey, COST_AMOUNT, snapshot.costAmount());
        stringRedisTemplate.opsForSet().add(dirtyKey, statsKey);
        stringRedisTemplate.expire(statsKey, STATS_TTL);
        stringRedisTemplate.expire(dirtyKey, STATS_TTL);
    }

    private StatsKeyParts parseStatsKey(String statsKey) {
        String[] parts = statsKey.split(":");
        if (parts.length != 6) {
            return null;
        }
        try {
            return new StatsKeyParts(
                    LocalDate.parse(parts[2], BASIC_DATE),
                    Long.valueOf(parts[3]),
                    Long.valueOf(parts[4]),
                    Long.valueOf(parts[5]));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            return Long.parseLong(text);
        }
        return 0L;
    }

    private record StatsSnapshot(
            long impressionCount,
            long clickCount,
            long conversionCount,
            long costAmount) {

        private boolean isEmpty() {
            return impressionCount == 0 && clickCount == 0 && conversionCount == 0 && costAmount == 0;
        }
    }

    private record StatsKeyParts(
            LocalDate statDate,
            Long planId,
            Long materialId,
            Long slotId) {
    }
}
