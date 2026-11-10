package com.example.adplatform.admin.response.slot;

public record SlotQueryResponse(
        String publicId,
        String slotCode,
        String name,
        Integer width,
        Integer height,
        String scene,
        Integer status) {
}
