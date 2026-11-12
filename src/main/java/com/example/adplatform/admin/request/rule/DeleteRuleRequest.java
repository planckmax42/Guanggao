package com.example.adplatform.admin.request.rule;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.adplatform.admin.json.AdminIdDeserializer;


public record DeleteRuleRequest(
        @NotNull @Positive @JsonDeserialize(using = AdminIdDeserializer.class) Long id) {
}
