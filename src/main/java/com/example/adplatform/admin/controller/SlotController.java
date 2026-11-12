package com.example.adplatform.admin.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import com.example.adplatform.admin.request.slot.DeleteSlotRequest;
import com.example.adplatform.admin.request.slot.CreateSlotRequest;
import com.example.adplatform.admin.request.slot.PageSlotRequest;
import com.example.adplatform.admin.request.slot.UpdateSlotRequest;
import com.example.adplatform.admin.request.slot.UpdateSlotStatusRequest;
import com.example.adplatform.admin.response.slot.SlotQueryResponse;
import com.example.adplatform.admin.service.SlotService;
import com.example.adplatform.admin.response.slot.SlotOperationResponse;
import com.example.adplatform.admin.response.PageResponse;
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

    @PostMapping("/create")
    public Result<SlotOperationResponse> create(@Valid @RequestBody CreateSlotRequest request) {
        return Result.success(slotService.create(request));
    }

    @PostMapping("/page")
    public Result<PageResponse<SlotQueryResponse>> page(@Valid @RequestBody PageSlotRequest request) {
        return Result.success(slotService.pageQuery(request));
    }

    @PutMapping("/update")
    public Result<SlotOperationResponse> update(@Valid @RequestBody UpdateSlotRequest request) {
        return Result.success(slotService.update(request));
    }

    @DeleteMapping("/delete")
    public Result<SlotOperationResponse> delete(@Valid @RequestBody DeleteSlotRequest request) {
        return Result.success(slotService.delete(request));
    }

    @PutMapping("/status")
    public Result<SlotOperationResponse> updateStatus(@Valid @RequestBody UpdateSlotStatusRequest request) {
        return Result.success(slotService.updateStatus(request));
    }
}
