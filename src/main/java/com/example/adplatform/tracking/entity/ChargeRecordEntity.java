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
 * 扣费流水表，记录每个可计费事件最终是否扣费以及实际扣费金额。
 */
@Getter
@Setter
@TableName("charge_record")
public class ChargeRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联 event.event_id，唯一约束用于防止同一事件重复扣费。
     */
    private String eventId;
    @TableField("plan_id")
    private Long planId;
    @TableField("material_id")
    private Long materialId;
    @TableField("slot_id")
    private Long slotId;
    private String billingType;

    /**
     * 实际扣费金额，单位为分。预算不足时为 0。
     */
    private Long amount;
    private String chargeStatus;
    private LocalDateTime chargeTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
