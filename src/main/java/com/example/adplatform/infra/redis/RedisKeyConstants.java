package com.example.adplatform.infra.redis;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class RedisKeyConstants {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private RedisKeyConstants() {
    }

    public static String viewerPlanFrequency(Long viewerId, Long planId, LocalDate date) {
        return "freq:viewer:%d:%d:%s".formatted(viewerId, planId, BASIC_DATE.format(date));
    }

    public static String planDailyBudget(LocalDate date, Long planId) {
        return "budget:daily:%s:%d".formatted(BASIC_DATE.format(date), planId);
    }

    public static String realtimePlanStats(LocalDate date, Long planId) {
        return "stats:rt:%s:%d".formatted(BASIC_DATE.format(date), planId);
    }

    public static String dailyStats(LocalDate date, Long planId, Long materialId, Long slotId) {
        return "stats:daily:%s:%d:%d:%d".formatted(BASIC_DATE.format(date), planId, materialId, slotId);
    }

    public static String dailyStatsDirtySet(LocalDate date) {
        return "stats:dirty:%s".formatted(BASIC_DATE.format(date));
    }

    public static String slotCodeToId(String slotCode) {
        return "slot:code:%s".formatted(slotCode);
    }
}
