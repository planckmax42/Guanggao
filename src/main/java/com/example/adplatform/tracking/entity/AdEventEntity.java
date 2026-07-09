package com.example.adplatform.tracking.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("ad_event")
public class AdEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String requestId;
    private String eventType;
    private Long campaignId;
    private Long creativeId;
    private Long adSlotId;
    private Long userId;
    private String billingType;
    private Integer charged;
    private Long costAmount;
    private LocalDateTime eventTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
