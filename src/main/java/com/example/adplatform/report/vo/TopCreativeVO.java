package com.example.adplatform.report.vo;

public record TopCreativeVO(
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
