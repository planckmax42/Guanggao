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
 * 广告主，表示在平台上投放广告的一方。
 */
@Getter
@Setter
@TableName("`user`")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 只在创建时生成的对外广告主标识。 */
    @Setter(AccessLevel.NONE)
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String publicId;

    public void initializePublicId(String publicId) {
        if (this.publicId != null) {
            throw new IllegalStateException("广告主 publicId 创建后不允许修改");
        }
        this.publicId = Objects.requireNonNull(publicId, "publicId");
    }

    /**
     * 广告主名称，例如某个品牌、公司或商家。
     */
    private String name;
    private String industry;
    private String contactName;
    private String contactEmail;

    /**
     * 广告主启停状态。停用后，其下广告计划不应继续投放。
     */
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
