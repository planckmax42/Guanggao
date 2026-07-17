package com.example.adplatform.delivery.response;

import java.util.List;

/**
 * 广告投放返回结果，包含本次请求 ID、候选数量和最终返回给前端的广告列表。
 */
public record AdDeliveryResponse(
        String requestId,
        Integer totalCandidates,
        Integer returnedCount,
        List<AdItemResponse> ads) {
}
