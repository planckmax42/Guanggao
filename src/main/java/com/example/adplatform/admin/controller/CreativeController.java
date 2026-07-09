package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.dto.AuditCreativeRequest;
import com.example.adplatform.admin.dto.CreateCreativeRequest;
import com.example.adplatform.admin.service.CreativeService;
import com.example.adplatform.admin.vo.CreativeVO;
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
@RequestMapping("/api/admin/creatives")
public class CreativeController {

    private final CreativeService creativeService;

    /**
     * 为已有广告计划创建广告素材，初始审核状态为待审核。
     */
    @PostMapping
    public Result<ResourceRefVO> create(@Valid @RequestBody CreateCreativeRequest request) {
        return Result.success(creativeService.create(request));
    }

    /**
     * 更新广告素材审核状态，可设置为待审核、审核通过或审核拒绝。
     */
    @PutMapping("/{id}/audit")
    public Result<CreativeVO> audit(
            @PathVariable Long id,
            @Valid @RequestBody AuditCreativeRequest request) {
        return Result.success(creativeService.audit(id, request));
    }

    /**
     * 分页查询广告素材，支持按广告计划 ID 和审核状态筛选。
     */
    @GetMapping("/page")
    public Result<PageResponse<CreativeVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) String auditStatus) {
        return Result.success(creativeService.pageQuery(current, size, campaignId, auditStatus));
    }
}
