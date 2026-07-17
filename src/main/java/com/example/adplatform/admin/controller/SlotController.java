package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.request.CreateSlotRequest;
import com.example.adplatform.admin.request.UpdateSlotRequest;
import com.example.adplatform.admin.service.SlotService;
import com.example.adplatform.admin.response.SlotResponse;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;
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
@RequestMapping("/api/admin/slots")
public class SlotController {

    private final SlotService slotService;

    /**
     * 创建广告位，用于表示客户端场景中的广告投放位置。
     */
    @PostMapping
    public Result<ResourceRefResponse> create(@Valid @RequestBody CreateSlotRequest request) {
        return Result.success(slotService.create(request));
    }

    /**
     * 更新广告位基础信息。修改广告位编码或启停状态后，会同步刷新投放链路使用的 Redis 缓存。
     */
    @PutMapping("/{id}")
    public Result<SlotResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSlotRequest request) {
        return Result.success(slotService.update(id, request));
    }

    /**
     * 分页查询广告位，支持按广告位编码关键字和启用状态筛选。
     */
    @GetMapping("/page")
    public Result<PageResponse<SlotResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String slotCode,
            @RequestParam(required = false) Integer status) {
        return Result.success(slotService.pageQuery(current, size, slotCode, status));
    }
}
