package com.example.adplatform.admin.response.slot;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** 广告主选择投放位置时所需的最小广告位视图。 */
public record AvailableSlotResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String slotCode,
        String name,
        Integer width,
        Integer height,
        String scene) {
}
