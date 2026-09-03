package com.example.adplatform.admin.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.example.adplatform.common.id.PublicIdGenerator.PLAN_PATTERN;

public record CreateRuleRequest(
        @NotBlank @Pattern(regexp = PLAN_PATTERN) String planPublicId,
        @Size(max = 32) List<@Size(max = 64) String> regions,
        @Size(max = 16) List<@Size(max = 32) String> deviceTypes,
        @Size(max = 32) String gender,
        @Min(0) @Max(120) Integer ageMin,
        @Min(0) @Max(120) Integer ageMax,
        @Size(max = 64) List<@Size(max = 64) String> userTags) {

    @AssertTrue(message = "最大年龄必须大于等于最小年龄")
    public boolean isValidAgeRange() {
        return ageMin == null || ageMax == null || ageMax >= ageMin;
    }
}
