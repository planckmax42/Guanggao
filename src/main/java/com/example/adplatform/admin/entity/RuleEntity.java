package com.example.adplatform.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.AccessLevel;
import java.util.Objects;
import java.util.List;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName(value = "`rule`", autoResultMap = true)
public class RuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @Setter(AccessLevel.NONE)
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String publicId;
    private String name;
    @TableField("advertiser_id")
    private String advertiserPublicId;
    @TableField(value = "device_type", typeHandler = JacksonTypeHandler.class)
    private List<String> deviceTypes;
    private String gender;
    private Integer ageMin;
    private Integer ageMax;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    public void initializePublicId(String publicId) {
        if (this.publicId != null) {
            throw new IllegalStateException("定向规则 publicId 创建后不允许修改");
        }
        this.publicId = Objects.requireNonNull(publicId, "publicId");
    }
}
