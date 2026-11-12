package com.example.adplatform.admin.entity;

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
@TableName("plan")
public class PlanEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @TableField("advertiser_id")
    private Long advertiserId;
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
