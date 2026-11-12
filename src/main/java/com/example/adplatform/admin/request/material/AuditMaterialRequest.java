package com.example.adplatform.admin.request.material;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.adplatform.admin.json.AdminIdDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;


public record AuditMaterialRequest(
        @NotNull @Positive @JsonDeserialize(using = AdminIdDeserializer.class) Long id,
        @NotBlank
        @Pattern(regexp = "APPROVED|REJECTED|PENDING", message = "审核状态只能是 APPROVED、REJECTED 或 PENDING")
        String auditStatus) {
}
