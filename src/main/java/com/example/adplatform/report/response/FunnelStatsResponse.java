package com.example.adplatform.report.response;

import java.time.LocalDate;

/**
 * 投放漏斗统计，用于观察曝光到点击、点击到转化的整体效果。
 */
public record FunnelStatsResponse(
        LocalDate startDate,
        LocalDate endDate,
        String planPublicId,
        Long impressionCount,
        Long clickCount,
        Long conversionCount,
        Long costAmount,
        Double ctr,
        Double cvr) {
}
