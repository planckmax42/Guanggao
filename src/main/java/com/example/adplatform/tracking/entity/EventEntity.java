package com.example.adplatform.tracking.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 广告原始事件表，记录曝光、点击、转化等用户行为明细。
 */
@Getter
@Setter
@TableName("`event`")
public class EventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 事件唯一 ID，由调用方生成，用于防止同一事件重复上报。
     */
    private String eventId;

    /**
     * 投放请求 ID，用于把一次广告召回和后续曝光、点击、转化串起来。
     */
    private String requestId;

    /**
     * 事件类型：IMPRESSION、CLICK、CONVERSION。
     */
    private String eventType;
    @TableField("plan_id")
    private Long planId;
    @TableField("material_id")
    private Long materialId;
    @TableField("slot_id")
    private Long slotId;
    @TableField("viewer_id")
    private Long viewerId;

    /**
     * 事件发生时广告计划使用的计费方式，保留快照方便后续审计。
     */
    private String billingType;

    private LocalDateTime eventTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
