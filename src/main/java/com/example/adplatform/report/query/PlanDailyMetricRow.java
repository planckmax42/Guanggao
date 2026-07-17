package com.example.adplatform.report.query;

import lombok.Getter;
import lombok.Setter;

/**
 * 计划当天聚合指标，用于广告召回阶段计算 CTR 排序分。
 */
@Getter
@Setter
public class PlanDailyMetricRow {

    private Long planId;
    private Long impressionCount;
    private Long clickCount;
}
