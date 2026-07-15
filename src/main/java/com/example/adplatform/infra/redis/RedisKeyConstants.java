package com.example.adplatform.infra.redis;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Redis Key 生成工具，统一维护各业务模块的 Key 前缀、字段顺序和日期格式。
 */
public final class RedisKeyConstants {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /** 工具类不允许实例化。 */
    private RedisKeyConstants() {
    }

    /**
     * 生成用户当日对指定广告计划的曝光频控 Key。
     *
     * @param viewerId 用户标识
     * @param planId 广告计划标识
     * @param date 统计日期
     * @return 频控计数 Key
     */
    public static String viewerPlanFrequency(Long viewerId, Long planId, LocalDate date) {
        return "freq:viewer:%d:%d:%s".formatted(viewerId, planId, BASIC_DATE.format(date));
    }

    /**
     * 生成广告计划的单日已消耗预算 Key。
     *
     * @param date 预算归属日期
     * @param planId 广告计划标识
     * @return 单日预算消耗 Key
     */
    public static String planDailyBudget(LocalDate date, Long planId) {
        return "budget:daily:%s:%d".formatted(BASIC_DATE.format(date), planId);
    }

    /**
     * 生成广告计划的累计已消耗预算 Key。
     *
     * @param planId 广告计划标识
     * @return 总预算消耗 Key
     */
    public static String planTotalBudget(Long planId) {
        return "budget:total:%d".formatted(planId);
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

    /**
     * 生成广告位编码到数据库主键的映射 Key。
     *
     * @param slotCode 对外广告位编码
     * @return 广告位缓存 Key
     */
    public static String slotCodeToId(String slotCode) {
        return "slot:code:%s".formatted(slotCode);
    }
}
