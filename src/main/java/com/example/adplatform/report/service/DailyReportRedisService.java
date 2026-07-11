package com.example.adplatform.report.service;

import java.time.LocalDate;

public interface DailyReportRedisService {

    /**
     * 将事件增量写入 Redis 实时统计，避免请求线程直接更新 MySQL 热点统计行。
     */
    void incrementDailyReport(
            LocalDate statDate,
            Long planId,
            Long materialId,
            Long slotId,
            long impressionCount,
            long clickCount,
            long conversionCount,
            long costAmount);

    /**
     * 将指定日期的 Redis 实时统计刷入 MySQL 日统计表。
     */
    void flushDailyReport(LocalDate statDate);
}
