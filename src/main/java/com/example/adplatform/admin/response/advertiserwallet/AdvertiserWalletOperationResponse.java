package com.example.adplatform.admin.response.advertiserwallet;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record AdvertiserWalletOperationResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id
) {}
