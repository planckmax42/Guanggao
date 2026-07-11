package com.example.adplatform.tracking.controller;

import com.example.adplatform.common.response.Result;
import com.example.adplatform.tracking.dto.AdEventRequest;
import com.example.adplatform.tracking.service.AdEventService;
import com.example.adplatform.tracking.vo.AdEventResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/tracking/events")
public class AdEventController {

    private final AdEventService adEventService;

    /**
     * 接收曝光、点击、转化事件，写入 Kafka 后立即返回；明细入库和统计累加由消费者异步完成。
     */
    @PostMapping
    public Result<AdEventResponse> collect(@Valid @RequestBody AdEventRequest request) {
        return Result.success(adEventService.collect(request));
    }
}
