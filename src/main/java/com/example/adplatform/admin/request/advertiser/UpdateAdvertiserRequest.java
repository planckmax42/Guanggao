package com.example.adplatform.admin.request.advertiser;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.example.adplatform.common.util.StringUtils.blankToNull;

import static com.example.adplatform.common.util.PublicIdUtils.ADVERTISER_PATTERN;

public record UpdateAdvertiserRequest(
        @NotBlank @Pattern(regexp = ADVERTISER_PATTERN) String publicId,
        @Size(max = 128) String name,
        @Size(max = 64) String industry,
        @Size(max = 64) String contactName,
        @Email @Size(max = 128) String contactEmail) {
    public UpdateAdvertiserRequest {
        name = blankToNull(name);
        industry = blankToNull(industry);
        contactName = blankToNull(contactName);
        contactEmail = blankToNull(contactEmail);
    }

    @AssertTrue(message = "请至少提供一个需要修改的字段")
    public boolean isUpdateProvided() {
        return name != null || industry != null || contactName != null || contactEmail != null;
    }
}
