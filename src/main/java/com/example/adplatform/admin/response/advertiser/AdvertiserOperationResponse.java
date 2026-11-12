package com.example.adplatform.admin.response.advertiser;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record AdvertiserOperationResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id
) {}
