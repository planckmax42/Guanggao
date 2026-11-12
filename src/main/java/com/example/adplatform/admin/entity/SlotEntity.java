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
 * 广告位，表示流量侧提供的一个可展示广告的位置。
 */
@Getter
@Setter
@TableName("slot")
public class SlotEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String slotCode;
    private String name;
    private Integer width;
    private Integer height;
    private String scene;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
