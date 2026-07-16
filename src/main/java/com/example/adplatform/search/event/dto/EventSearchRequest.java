package com.example.adplatform.search.event.dto;

import java.time.LocalDateTime;

/**
 * 事件检索条件。所有维度均为可选，默认检索最近 24 小时。
 *
 * @param cursor 上一页返回的不透明 search_after 游标
 * @param size 单页数量，服务端最多允许 100
 */
public record EventSearchRequest(
        String eventId,
        String requestId,
        String eventType,
        Long planId,
        Long materialId,
        Long slotId,
        Long viewerId,
        Boolean charged,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String cursor,
        Integer size) {
}
