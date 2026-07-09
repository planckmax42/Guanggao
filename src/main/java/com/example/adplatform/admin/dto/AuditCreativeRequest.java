package com.example.adplatform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AuditCreativeRequest(
        @NotBlank
        @Pattern(regexp = "APPROVED|REJECTED|PENDING", message = "审核状态只能是 APPROVED、REJECTED 或 PENDING")
        String auditStatus) {
}
