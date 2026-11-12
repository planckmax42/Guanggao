package com.example.adplatform.admin.response.material;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record MaterialQueryResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long advertiserId,
        @JsonSerialize(using = ToStringSerializer.class) Long slotId,
        String name,
        String description,
        String imageUrl,
        String landingPageUrl,
        String auditStatus,
        Integer status) {
}
