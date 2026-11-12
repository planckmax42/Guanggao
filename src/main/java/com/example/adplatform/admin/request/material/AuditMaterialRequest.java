package com.example.adplatform.admin.request.material;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import static com.example.adplatform.common.util.PublicIdUtils.MATERIAL_PATTERN;

public record AuditMaterialRequest(
        @NotBlank @Pattern(regexp = MATERIAL_PATTERN) String publicId,
        @NotBlank
        @Pattern(regexp = "APPROVED|REJECTED|PENDING", message = "审核状态只能是 APPROVED、REJECTED 或 PENDING")
        String auditStatus) {
}
