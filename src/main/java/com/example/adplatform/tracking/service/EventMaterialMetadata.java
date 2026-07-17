package com.example.adplatform.tracking.service;

/** 三条事件消费链路共享的素材、广告位和计划快照。 */
public record EventMaterialMetadata(
        Long planId,
        Long slotId,
        Long budgetTotal,
        Long budgetDaily,
        Long bidPrice,
        String billingType) {
}
