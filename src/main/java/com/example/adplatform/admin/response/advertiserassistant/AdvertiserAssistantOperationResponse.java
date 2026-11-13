package com.example.adplatform.admin.response.advertiserassistant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record AdvertiserAssistantOperationResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id
) {}
