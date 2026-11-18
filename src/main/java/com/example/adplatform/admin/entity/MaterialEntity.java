package com.example.adplatform.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;


@Getter
@Setter
@TableName(value = "material", autoResultMap = true)
public class MaterialEntity {

    @TableId
    private Long id;
    @TableField("advertiser_id")
    private Long advertiserId;
    @TableField("slot_id")
    private Long slotId;
    private String name;
    private String description;
    private String imageUrl;
    private String landingPageUrl;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    private String auditStatus;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
