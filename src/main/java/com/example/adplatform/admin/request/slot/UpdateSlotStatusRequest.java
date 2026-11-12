package com.example.adplatform.admin.request.slot;

import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.adplatform.admin.json.AdminIdDeserializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


public record UpdateSlotStatusRequest(
        @NotNull @Positive @JsonDeserialize(using = AdminIdDeserializer.class) Long id,
        @NotBlank String slotCode,
        @NotNull @Min(0) @Max(1) Integer status) {
}
