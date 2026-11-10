package com.example.adplatform.admin.request.advertiser;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.example.adplatform.common.id.PublicIdGenerator.ADVERTISER_PATTERN;

public record UpdateAdvertiserRequest(
        @NotBlank @Pattern(regexp = ADVERTISER_PATTERN) String publicId,
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 64) String industry,
        @NotBlank @Size(max = 64) String contactName,
        @NotBlank @Email @Size(max = 128) String contactEmail) {
}
