package com.example.adplatform.report.vo;

import java.time.LocalDate;

/**
 * 投放漏斗统计，用于观察曝光到点击、点击到转化的整体效果。
 */
public record FunnelStatsVO(
        LocalDate startDate,
        LocalDate endDate,
        Long campaignId,
        Long impressionCount,
        Long clickCount,
        Long conversionCount,
        Long costAmount,
        Double ctr,
        Double cvr) {
}
