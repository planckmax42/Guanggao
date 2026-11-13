package com.example.adplatform.admin.request.advertiserassistant;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.adplatform.admin.json.AdminIdDeserializer;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import static com.example.adplatform.common.util.StringUtils.blankToNull;

public record UpdateAdvertiserAssistantRequest(
        @NotNull @Positive @JsonDeserialize(using = AdminIdDeserializer.class) Long id,
        @Size(max = 64) String username,
        @Size(max = 64) String name,
        @Size(max = 32) String role) {
    public UpdateAdvertiserAssistantRequest {
        username = blankToNull(username);
        name = blankToNull(name);
        role = blankToNull(role);
    }

    @AssertTrue(message = "请至少提供一个需要修改的字段")
    public boolean isUpdateProvided() {
        return username != null || name != null || role != null;
    }
}
