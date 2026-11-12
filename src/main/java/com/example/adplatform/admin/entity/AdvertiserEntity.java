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
import java.time.LocalDateTime;
import java.util.Objects;


@Getter
@Setter
@TableName("advertiser")
public class AdvertiserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @Setter(AccessLevel.NONE)
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String publicId;
    private String name;
    private String industry;
    private String contactName;
    private String contactEmail;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    public void initializePublicId(String publicId) {
        if (this.publicId != null) {
            throw new IllegalStateException("广告主 publicId 创建后不允许修改");
        }
        this.publicId = Objects.requireNonNull(publicId, "publicId");
    }
}
