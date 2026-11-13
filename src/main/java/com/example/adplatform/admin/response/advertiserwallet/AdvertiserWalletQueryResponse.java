package com.example.adplatform.admin.response.advertiserwallet;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record AdvertiserWalletQueryResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long advertiserId,
        Long balanceMicro,
        Long frozenMicro) {
}
