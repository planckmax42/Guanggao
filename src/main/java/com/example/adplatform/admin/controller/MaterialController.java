package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.request.CreateMaterialRequest;
import com.example.adplatform.admin.service.MaterialService;
import com.example.adplatform.admin.response.MaterialResponse;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;
import com.example.adplatform.common.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.example.adplatform.common.id.PublicIdGenerator.PLAN_PATTERN;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/advertiser/materials")
public class MaterialController {

    private final MaterialService materialService;

    /**
     * 为已有广告计划创建广告素材，初始审核状态为待审核。
     */
    @PostMapping
    public Result<ResourceRefResponse> create(@Valid @RequestBody CreateMaterialRequest request) {
        return Result.success(materialService.create(request));
    }

    /**
     * 分页查询广告素材，支持按广告计划公开标识和审核状态筛选。
     */
    @GetMapping("/page")
    public Result<PageResponse<MaterialResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) @Pattern(regexp = PLAN_PATTERN) String planPublicId,
            @RequestParam(required = false) String auditStatus) {
        return Result.success(materialService.pageQuery(current, size, planPublicId, auditStatus));
    }
}
