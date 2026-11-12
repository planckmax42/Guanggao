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

@Getter
@Setter
@TableName("plan")
public class PlanEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @Setter(AccessLevel.NONE)
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String publicId;
    public void initializePublicId(String publicId) {
        if (this.publicId != null) {
            throw new IllegalStateException("广告计划 publicId 创建后不允许修改");
        }
        this.publicId = Objects.requireNonNull(publicId, "publicId");
    }
    @TableField("advertiser_id")
    private String advertiserPublicId;
    private String name;
    private Long budgetTotal;
    private Long budgetDaily;
    private Long bidPrice;
    private String billingType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
