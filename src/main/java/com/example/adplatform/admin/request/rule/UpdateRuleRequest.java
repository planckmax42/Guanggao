package com.example.adplatform.admin.request.rule;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.adplatform.admin.json.AdminIdDeserializer;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.example.adplatform.common.util.StringUtils.blankToNull;

public record UpdateRuleRequest(
        @NotNull @Positive @JsonDeserialize(using = AdminIdDeserializer.class) Long id,
        @Size(max = 128) String name,
        @Size(min = 1 ,max = 16) List<@Size(max = 32)@NotBlank String> deviceTypes,
        @Size(max = 32) String gender,
        @Min(0) @Max(120) Integer ageMin,
        @Min(0) @Max(120) Integer ageMax) {

    public UpdateRuleRequest {
        name = blankToNull(name);
        gender = blankToNull(gender);
    }

    @AssertTrue(message = "请至少提供一个需要修改的字段")
    public boolean isUpdateProvided() {
        return name != null || deviceTypes != null || gender != null || ageMin != null || ageMax != null;
    }

    @AssertTrue(message = "最大年龄必须大于等于最小年龄")
    public boolean isValidAgeRange() {
        return ageMin == null || ageMax == null || ageMax >= ageMin;
    }
}
