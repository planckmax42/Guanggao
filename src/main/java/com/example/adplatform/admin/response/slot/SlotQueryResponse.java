package com.example.adplatform.admin.response.slot;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record SlotQueryResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String slotCode,
        String name,
        Integer width,
        Integer height,
        String scene,
        Integer status) {
}
