package com.example.adplatform.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@TableName(value = "user", autoResultMap = true)
public class UserEntity {
    @TableId
    private Long id;
    private String name;
    @TableField("birth_date")
    private LocalDate birthDate;
    private String address;
    private String phone;
    @TableField(value = "device_type" , typeHandler = JacksonTypeHandler.class)
    private List<String> deviceType;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tag;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
