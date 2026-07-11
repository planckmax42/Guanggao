package com.example.adplatform.admin.entity;

/**
 * 广告计划生命周期状态。
 */
public enum PlanStatus {

    /**
     * 草稿：计划刚创建，配置还未确认，不参与广告召回和投放。
     */
    DRAFT,

    /**
     * 投放中：计划已上线，在时间、预算、定向等条件满足时可以参与投放。
     */
    ONLINE,

    /**
     * 已暂停：临时停止投放，配置保留，后续可以重新上线。
     */
    PAUSED,

    /**
     * 已下线：计划结束投放，通常表示生命周期结束，不再参与召回。
     */
    OFFLINE
}
