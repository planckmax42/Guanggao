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

@Getter
@Setter
@TableName("ad_stats_daily")
public class AdStatsDailyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate statDate;
    private Long campaignId;
    private Long creativeId;
    private Long adSlotId;
    private Long impressionCount;
    private Long clickCount;
    private Long conversionCount;
    private Long costAmount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
