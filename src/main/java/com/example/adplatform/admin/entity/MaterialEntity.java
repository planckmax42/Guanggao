package com.example.adplatform.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 广告素材，表示真正展示给用户的广告内容。
 */
@Getter
@Setter
@TableName("material")
public class MaterialEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属广告计划 ID，素材跟随计划的预算、出价和投放状态。
     */
    @TableField("plan_id")
    private Long planId;

    /**
     * 绑定广告位 ID，表示该素材适配哪个展示位置和尺寸。
     */
    @TableField("slot_id")
    private Long slotId;
    private String title;
    private String description;
    private String imageUrl;
    private String landingPageUrl;

    /**
     * 审核状态，控制素材内容是否合规。未通过审核的素材不能投放。
     */
    private String auditStatus;

    /**
     * 启停状态，控制素材是否临时启用。它和审核状态共同决定素材能否参与召回。
     */
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
