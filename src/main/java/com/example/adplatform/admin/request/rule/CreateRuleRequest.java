package com.example.adplatform.admin.request.rule;

import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.adplatform.admin.json.AdminIdDeserializer;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;


public record CreateRuleRequest(
        @NotNull @Positive @JsonDeserialize(using = AdminIdDeserializer.class) Long advertiserId,
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
