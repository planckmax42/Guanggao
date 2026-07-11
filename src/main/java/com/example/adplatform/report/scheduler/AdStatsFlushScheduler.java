package com.example.adplatform.report.scheduler;

import com.example.adplatform.report.service.AdStatsRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@RequiredArgsConstructor
@Component
public class AdStatsFlushScheduler {

    private final AdStatsRedisService adStatsRedisService;

    /**
     * 定时把 Redis 实时统计刷入 MySQL，报表接口继续读取 ad_stats_daily。
     */
    @Scheduled(
            fixedDelayString = "${app.stats.flush-delay-ms:10000}",
            initialDelayString = "${app.stats.flush-initial-delay-ms:10000}")
    public void flushTodayStats() {
        try {
            adStatsRedisService.flushDailyStats(LocalDate.now());
        } catch (RuntimeException ex) {
            log.warn("Redis 实时统计刷入 MySQL 失败，将在下次任务重试", ex);
        }
    }
}
