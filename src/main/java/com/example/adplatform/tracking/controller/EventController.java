package com.example.adplatform.tracking.controller;

import com.example.adplatform.common.response.Result;
import com.example.adplatform.tracking.request.EventRequest;
import com.example.adplatform.tracking.service.EventService;
import com.example.adplatform.tracking.response.EventResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletionStage;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/tracking/events")
public class EventController {

    private final EventService eventService;

    /**
     * 接收曝光、点击、转化事件；Broker 确认写入后返回，明细入库和统计累加由后台处理器完成。
     */
    @PostMapping
    public CompletionStage<Result<EventResponse>> collect(
            @Valid @RequestBody EventRequest request) {
        return eventService.collect(request).thenApply(Result::success);
    }
}
