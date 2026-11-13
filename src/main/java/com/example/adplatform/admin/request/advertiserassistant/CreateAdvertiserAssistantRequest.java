package com.example.adplatform.admin.request.advertiserassistant;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.adplatform.admin.json.AdminIdDeserializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAdvertiserAssistantRequest(
        @NotNull @Positive @JsonDeserialize(using = AdminIdDeserializer.class) Long advertiserId,
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 64) String name,
        @NotBlank @Size(max = 32) String role) {
}
