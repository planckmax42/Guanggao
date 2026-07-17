package com.example.adplatform.delivery.response;

/**
 * 单条可展示广告，前端或流量侧拿到后用于渲染广告内容并上报事件。
 */
public record AdItemResponse(
        Long planId,
        Long materialId,
        Long slotId,
        String title,
        String description,
        String imageUrl,
        String landingPageUrl,
        Long bidPrice,
        Double score) {
}
