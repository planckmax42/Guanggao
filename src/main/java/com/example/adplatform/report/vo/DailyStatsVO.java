package com.example.adplatform.report.vo;

import java.time.LocalDate;

public record DailyStatsVO(
        LocalDate statDate,
        Long campaignId,
        Long creativeId,
        Long adSlotId,
        Long impressionCount,
        Long clickCount,
        Long conversionCount,
        Long costAmount,
        Double ctr,
        Double cvr) {
}
