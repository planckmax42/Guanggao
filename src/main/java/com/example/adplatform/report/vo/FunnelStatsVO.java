package com.example.adplatform.report.vo;

import java.time.LocalDate;

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
