package com.example.adplatform.admin.request.advertiserwallet;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.adplatform.admin.json.AdminIdDeserializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateAdvertiserWalletRequest(
        @NotNull @Positive @JsonDeserialize(using = AdminIdDeserializer.class) Long advertiserId) {
}
