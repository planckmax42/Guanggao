package com.example.adplatform.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.AccessLevel;

import java.util.Objects;

import java.time.LocalDateTime;

/**
 * 广告计划，承载预算、出价、投放时间和生命周期状态。
 */
@Getter
@Setter
@TableName("plan")
public class PlanEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 只在创建时生成的对外计划标识。 */
    @Setter(AccessLevel.NONE)
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String publicId;

    public void initializePublicId(String publicId) {
        if (this.publicId != null) {
            throw new IllegalStateException("广告计划 publicId 创建后不允许修改");
        }
        this.publicId = Objects.requireNonNull(publicId, "publicId");
    }

    /**
     * 所属广告主 ID，一个广告主可以创建多个广告计划。
     */
    @TableField("user_id")
    private Long userId;
    private String name;

    /**
     * 总预算，单位为分，避免使用浮点数存金额。
     */
    private Long budgetTotal;

    /**
     * 单日预算，单位为分，用于控制每天最多消耗多少广告费。
     */
    private Long budgetDaily;

    /**
     * 出价，单位为分；排序时作为基础竞争力，扣费时作为一次计费金额。
     */
    private Long bidPrice;

    /**
     * 计费方式：CPC、CPM、CPA，决定哪类事件会触发扣费。
     */
    private String billingType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /**
     * 广告计划生命周期状态，只有 ONLINE 且在投放时间内才会参与召回。
     */
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
