package com.example.adplatform.admin.request.advertiser;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import static com.example.adplatform.common.util.PublicIdUtils.ADVERTISER_PATTERN;

public record DeleteAdvertiserRequest(
        @NotBlank @Pattern(regexp = ADVERTISER_PATTERN) String publicId) {
}
