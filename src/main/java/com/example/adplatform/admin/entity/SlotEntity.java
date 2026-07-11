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

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 对外使用的广告位编码。投放接口使用它定位广告位，避免直接暴露数据库主键。
     */
    private String slotCode;

    /**
     * 广告位名称，例如首页 Banner、详情页信息流。
     */
    private String name;

    /**
     * 广告位素材尺寸要求，单位为像素。
     */
    private Integer width;
    private Integer height;

    /**
     * 广告展示场景，用于后台管理和后续扩展定向策略。
     */
    private String scene;

    /**
     * 广告位启停状态。停用后，该广告位不再返回广告。
     */
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
