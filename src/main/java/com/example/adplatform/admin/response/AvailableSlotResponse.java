package com.example.adplatform.admin.response;

/** 广告主选择投放位置时所需的最小广告位视图。 */
public record AvailableSlotResponse(
        String publicId,
        String slotCode,
        String name,
        Integer width,
        Integer height,
        String scene) {
}
