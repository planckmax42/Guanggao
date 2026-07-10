package com.example.adplatform.report.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 广告日统计汇总表，按日期、计划、素材、广告位聚合事件数据。
 */
@Getter
@Setter
@TableName("ad_stats_daily")
public class AdStatsDailyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 统计日期，来自事件发生时间。
     */
    private LocalDate statDate;
    private Long campaignId;
    private Long creativeId;
    private Long adSlotId;

    /**
     * 曝光、点击、转化计数，用于报表和 CTR/CVR 计算。
     */
    private Long impressionCount;
    private Long clickCount;
    private Long conversionCount;

    /**
     * 已消耗金额，单位为分。
     */
    private Long costAmount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
