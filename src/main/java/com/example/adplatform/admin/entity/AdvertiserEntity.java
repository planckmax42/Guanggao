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
 * 广告主，表示在平台上投放广告的一方。
 */
@Getter
@Setter
@TableName("advertiser")
public class AdvertiserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

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
