package com.example.adplatform.admin.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRuleRequest(
        @NotNull Long planId,
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
