package com.example.adplatform.admin.request.advertiserwallet;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.adplatform.admin.json.AdminIdDeserializer;
import com.example.adplatform.admin.json.WalletAmountDeserializer;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;

public record UpdateAdvertiserWalletRequest(
        @NotNull @Positive @JsonDeserialize(using = AdminIdDeserializer.class) Long id,
        @Min(0) @JsonDeserialize(using = WalletAmountDeserializer.class) Long balanceMicro,
        @Min(0) @JsonDeserialize(using = WalletAmountDeserializer.class) Long frozenMicro) {
    @AssertTrue(message = "请至少提供一个需要修改的字段")
    public boolean isUpdateProvided() {
        return balanceMicro != null || frozenMicro != null;
    }
}
