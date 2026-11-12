package com.example.adplatform.admin.request.rule;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.example.adplatform.common.util.PublicIdUtils.ADVERTISER_PATTERN;

public record CreateRuleRequest(
        @NotBlank @Pattern(regexp = ADVERTISER_PATTERN) String advertiserPublicId,
        @NotBlank @Size(max = 128) String name,
        @NotEmpty @Size(max = 16) List<@NotBlank @Size(max = 32) String> deviceTypes,
        @NotBlank @Size(max = 32) String gender,
        @NotNull @Min(0) @Max(120) Integer ageMin,
        @NotNull @Min(0) @Max(120) Integer ageMax) {

    @AssertTrue(message = "最大年龄必须大于等于最小年龄")
    public boolean isValidAgeRange() {
        return ageMin == null || ageMax == null || ageMax >= ageMin;
    }
}
