package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.request.AuditMaterialRequest;
import com.example.adplatform.admin.response.MaterialResponse;
import com.example.adplatform.admin.service.MaterialService;
import com.example.adplatform.common.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.example.adplatform.common.id.PublicIdGenerator.MATERIAL_PATTERN;

/** 平台运营人员的素材审核入口。 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/platform/materials")
public class MaterialReviewController {

    private final MaterialService materialService;

    @PutMapping("/{publicId}/audit")
    public Result<MaterialResponse> audit(
            @PathVariable @Pattern(regexp = MATERIAL_PATTERN) String publicId,
            @Valid @RequestBody AuditMaterialRequest request) {
        return Result.success(materialService.audit(publicId, request));
    }
}
