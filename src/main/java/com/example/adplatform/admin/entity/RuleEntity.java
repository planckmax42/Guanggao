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
 * 广告定向规则，用来描述某个广告计划适合投给哪些用户。
 */
@Getter
@Setter
@TableName("`rule`")
public class RuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 只在创建时生成的对外定向规则标识。 */
    @Setter(AccessLevel.NONE)
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String publicId;

    public void initializePublicId(String publicId) {
        if (this.publicId != null) {
            throw new IllegalStateException("定向规则 publicId 创建后不允许修改");
        }
        this.publicId = Objects.requireNonNull(publicId, "publicId");
    }

    /**
     * 所属广告计划 ID。当前设计中一个计划对应一组定向规则。
     */
    @TableField("plan_id")
    private Long planId;

    /**
     * 地域白名单，JSON 数组格式，例如 ["beijing","shanghai"]；为空表示不限地域。
     */
    private String region;

    /**
     * 设备类型白名单，JSON 数组格式，例如 ["ios","android"]；为空表示不限设备。
     */
    private String deviceType;

    /**
     * 性别定向。为空表示不限性别。
     */
    private String gender;
    private Integer ageMin;
    private Integer ageMax;

    /**
     * 用户标签白名单，JSON 数组格式。用户命中任意一个标签即可通过定向。
     */
    private String userTags;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
