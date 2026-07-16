package com.example.adplatform.search.event.vo;

import java.time.LocalDateTime;

/** 运营事件检索返回项。 */
public record EventSearchItemVO(
        String eventId,
        String requestId,
        String eventType,
        Long planId,
        Long materialId,
        Long slotId,
        Long viewerId,
        String billingType,
        Boolean charged,
        Long costAmount,
        LocalDateTime eventTime,
        LocalDateTime createdAt) {
}
