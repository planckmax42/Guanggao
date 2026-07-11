package com.example.adplatform.admin.entity;

/**
 * 广告素材审核状态。
 */
public enum MaterialAuditStatus {

    /**
     * 待审核：素材已提交但还没有审核结果，不能参与投放。
     */
    PENDING,

    /**
     * 审核通过：素材内容合规，可以在计划上线且素材启用时参与投放。
     */
    APPROVED,

    /**
     * 审核拒绝：素材不符合投放要求，不能参与投放。
     */
    REJECTED
}
