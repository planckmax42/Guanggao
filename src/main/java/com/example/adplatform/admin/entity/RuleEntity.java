package com.example.adplatform.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName(value = "`rule`", autoResultMap = true)
public class RuleEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    @TableField("advertiser_id")
    private Long advertiserId;
    @TableField(value = "device_type", typeHandler = JacksonTypeHandler.class)
    private List<String> deviceTypes;
    private String gender;
    private Integer ageMin;
    private Integer ageMax;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
