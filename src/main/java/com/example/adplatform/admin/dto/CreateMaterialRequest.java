package com.example.adplatform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMaterialRequest(
        @NotNull Long planId,
        @NotNull Long slotId,
        @NotBlank @Size(max = 128) String title,
        @NotBlank @Size(max = 512) String description,
        @NotBlank @Size(max = 512) String imageUrl,
        @NotBlank @Size(max = 512) String landingPageUrl) {
}
