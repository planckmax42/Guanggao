package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.response.AvailableSlotResponse;
import com.example.adplatform.admin.service.SlotService;
import com.example.adplatform.common.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 广告主只读的可投放广告位入口。 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/advertiser/available-slots")
public class AvailableSlotController {

    private final SlotService slotService;

    @GetMapping
    public Result<List<AvailableSlotResponse>> list() {
        return Result.success(slotService.listAvailable());
    }
}
