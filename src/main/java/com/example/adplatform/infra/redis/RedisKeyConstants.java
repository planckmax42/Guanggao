package com.example.adplatform.infra.redis;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class RedisKeyConstants {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private RedisKeyConstants() {
    }

    public static String userCampaignFrequency(Long userId, Long campaignId, LocalDate date) {
        return "ad:freq:user:%d:%d:%s".formatted(userId, campaignId, BASIC_DATE.format(date));
    }

    public static String campaignDailyBudget(LocalDate date, Long campaignId) {
        return "ad:budget:daily:%s:%d".formatted(BASIC_DATE.format(date), campaignId);
    }

    public static String realtimeCampaignStats(LocalDate date, Long campaignId) {
        return "ad:stats:rt:%s:%d".formatted(BASIC_DATE.format(date), campaignId);
    }

    public static String dailyStats(LocalDate date, Long campaignId, Long creativeId, Long adSlotId) {
        return "ad:stats:daily:%s:%d:%d:%d".formatted(BASIC_DATE.format(date), campaignId, creativeId, adSlotId);
    }

    public static String dailyStatsDirtySet(LocalDate date) {
        return "ad:stats:dirty:%s".formatted(BASIC_DATE.format(date));
    }
}
