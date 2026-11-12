package com.example.adplatform.admin.request.material;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.adplatform.admin.json.AdminIdDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record CreateMaterialRequest(
        @NotNull @Positive @JsonDeserialize(using = AdminIdDeserializer.class) Long advertiserId,
        @NotNull @Positive @JsonDeserialize(using = AdminIdDeserializer.class) Long slotId,
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 512) String description,
        @NotBlank @Size(max = 512) String imageUrl,
        @NotBlank @Size(max = 512) String landingPageUrl) {
}
