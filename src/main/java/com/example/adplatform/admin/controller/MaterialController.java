package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.dto.AuditMaterialRequest;
import com.example.adplatform.admin.dto.CreateMaterialRequest;
import com.example.adplatform.admin.service.MaterialService;
import com.example.adplatform.admin.vo.MaterialVO;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefVO;
import com.example.adplatform.common.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/materials")
public class MaterialController {

    private final MaterialService materialService;

    /**
     * 为已有广告计划创建广告素材，初始审核状态为待审核。
     */
    @PostMapping
    public Result<ResourceRefVO> create(@Valid @RequestBody CreateMaterialRequest request) {
        return Result.success(materialService.create(request));
    }

    /**
     * 更新广告素材审核状态，可设置为待审核、审核通过或审核拒绝。
     */
    @PutMapping("/{id}/audit")
    public Result<MaterialVO> audit(
            @PathVariable Long id,
            @Valid @RequestBody AuditMaterialRequest request) {
        return Result.success(materialService.audit(id, request));
    }

    /**
     * 分页查询广告素材，支持按广告计划 ID 和审核状态筛选。
     */
    @GetMapping("/page")
    public Result<PageResponse<MaterialVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) String auditStatus) {
        return Result.success(materialService.pageQuery(current, size, planId, auditStatus));
    }
}
