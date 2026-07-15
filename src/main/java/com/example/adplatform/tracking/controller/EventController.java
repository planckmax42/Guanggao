package com.example.adplatform.tracking.controller;

import com.example.adplatform.common.response.Result;
import com.example.adplatform.tracking.dto.EventRequest;
import com.example.adplatform.tracking.service.EventService;
import com.example.adplatform.tracking.vo.EventResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/tracking/events")
public class EventController {

    private final EventService eventService;

    /**
     * 接收曝光、点击、转化事件，发布异步事件后立即返回；明细入库和统计累加由后台处理器完成。
     */
    @PostMapping
    public Result<EventResponse> collect(@Valid @RequestBody EventRequest request) {
        return Result.success(eventService.collect(request));
    }
}
