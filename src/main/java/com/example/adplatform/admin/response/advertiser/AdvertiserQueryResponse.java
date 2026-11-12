package com.example.adplatform.admin.response.advertiser;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record AdvertiserQueryResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String name,
        String industry,
        String contactName,
        String contactEmail,
        Integer status) {
}
