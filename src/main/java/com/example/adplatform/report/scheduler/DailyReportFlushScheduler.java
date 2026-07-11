package com.example.adplatform.report.scheduler;

import com.example.adplatform.report.service.DailyReportRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@RequiredArgsConstructor
@Component
public class DailyReportFlushScheduler {

    private final DailyReportRedisService dailyReportRedisService;

    /**
     * 定时把 Redis 实时统计刷入 MySQL，报表接口继续读取 daily_report。
     */
    @Scheduled(
            fixedDelayString = "${app.stats.flush-delay-ms:10000}",
            initialDelayString = "${app.stats.flush-initial-delay-ms:10000}")
    public void flushTodayStats() {
        try {
            dailyReportRedisService.flushDailyReport(LocalDate.now());
        } catch (RuntimeException ex) {
            log.warn("Redis 实时统计刷入 MySQL 失败，将在下次任务重试", ex);
        }
    }
}
