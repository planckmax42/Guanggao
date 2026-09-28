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
 * 广告位，表示流量侧提供的一个可展示广告的位置。
 */
@Getter
@Setter
@TableName("slot")
public class SlotEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @Setter(AccessLevel.NONE)
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String publicId;
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
    public void initializePublicId(String publicId) {
        this.publicId = Objects.requireNonNull(publicId, "publicId");
    }
}
