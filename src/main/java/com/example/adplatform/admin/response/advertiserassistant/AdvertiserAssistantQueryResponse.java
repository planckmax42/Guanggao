package com.example.adplatform.admin.response.advertiserassistant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record AdvertiserAssistantQueryResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long advertiserId,
        String username,
        String name,
        String role,
        Integer status) {
}
