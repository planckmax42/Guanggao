package com.example.adplatform.report.service;

import java.time.LocalDate;

public interface AdStatsRedisService {

    /**
     * 将事件增量写入 Redis 实时统计，避免请求线程直接更新 MySQL 热点统计行。
     */
    void incrementDailyStats(
            LocalDate statDate,
            Long campaignId,
            Long creativeId,
            Long adSlotId,
            long impressionCount,
            long clickCount,
            long conversionCount,
            long costAmount);

    /**
     * 将指定日期的 Redis 实时统计刷入 MySQL 日统计表。
     */
    void flushDailyStats(LocalDate statDate);
}
