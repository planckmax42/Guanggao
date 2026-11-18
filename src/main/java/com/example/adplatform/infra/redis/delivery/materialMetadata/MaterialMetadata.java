package com.example.adplatform.infra.redis.delivery.materialMetadata;

/**
 * 单条可展示广告，前端或流量侧拿到后用于渲染广告内容并上报事件。
 */
public record MaterialMetadata(
        Long materialId,
        String name,
        String description,
        String imageUrl,
        String landingPageUrl) {
}
