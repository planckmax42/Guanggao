package com.example.adplatform.infra.redis.report;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Redis 报表链路 Key 生成工具。 */
public final class ReportRedisKeys {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /** 工具类不允许实例化。 */
    private ReportRedisKeys() {
    }

    /**
     * 生成广告计划当日实时统计 Key。
     *
     * @param date 统计日期
     * @param planId 广告计划标识
     * @return 计划实时统计 Key
     */
    public static String realtimePlanStats(LocalDate date, Long planId) {
        return "stats:rt:%s:%d".formatted(BASIC_DATE.format(date), planId);
    }

    /**
     * 生成计划、素材和广告位维度的每日统计 Key。
     *
     * @param date 统计日期
     * @param planId 广告计划标识
     * @param materialId 广告素材标识
     * @param slotId 广告位标识
     * @return 多维每日统计 Key
     */
    public static String dailyStats(LocalDate date, Long planId, Long materialId, Long slotId) {
        return "stats:daily:%s:%d:%d:%d".formatted(BASIC_DATE.format(date), planId, materialId, slotId);
    }

    /**
     * 生成待落库每日统计 Key 的脏集合 Key。
     *
     * @param date 统计日期
     * @return 当日脏数据集合 Key
     */
    public static String dailyStatsDirtySet(LocalDate date) {
        return "stats:dirty:%s".formatted(BASIC_DATE.format(date));
    }
}
