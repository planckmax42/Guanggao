package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.request.slot.CreateSlotRequest;
import com.example.adplatform.admin.request.slot.PageSlotRequest;
import com.example.adplatform.admin.request.slot.UpdateSlotRequest;
import com.example.adplatform.admin.request.slot.UpdateSlotStatusRequest;
import com.example.adplatform.admin.response.slot.SlotQueryResponse;
import com.example.adplatform.admin.service.SlotService;
import com.example.adplatform.admin.response.slot.SlotOperationResponse;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;
import com.example.adplatform.common.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/platform/slots")
public class SlotController {

    private final SlotService slotService;

    /**
     * 创建广告位，用于表示客户端场景中的广告投放位置。
     */
    @PostMapping
    public Result<SlotOperationResponse> create(@Valid @RequestBody CreateSlotRequest request) {
        return Result.success(slotService.create(request));
    }

    /**
     * 更新广告位基础信息。修改广告位编码后，会同步刷新投放链路使用的 Redis 缓存。
     */
    @PutMapping
    public Result<SlotOperationResponse> update(@Valid @RequestBody UpdateSlotRequest request) {
        return Result.success(slotService.update(request));
    }

    /**
     * 单独更新广告位启停状态。
     */
    @PutMapping("/status")
    public Result<SlotOperationResponse> updateStatus(@Valid @RequestBody UpdateSlotStatusRequest request) {
        return Result.success(slotService.updateStatus(request));
    }

    /**
     * 分页查询广告位，支持按广告位编码关键字和启用状态筛选。
     */
    @PostMapping("/page")
    public Result<PageResponse<SlotQueryResponse>> page(@Valid @RequestBody PageSlotRequest request) {
        return Result.success(slotService.pageQuery(request));
    }
}
