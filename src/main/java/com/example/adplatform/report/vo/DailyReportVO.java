package com.example.adplatform.report.vo;

import java.time.LocalDate;

/**
 * 单日广告明细统计，按日期、计划、素材、广告位维度返回。
 */
public record DailyReportVO(
        LocalDate statDate,
        Long planId,
        Long materialId,
        Long slotId,
        Long impressionCount,
        Long clickCount,
        Long conversionCount,
        Long costAmount,
        Double ctr,
        Double cvr) {
}
