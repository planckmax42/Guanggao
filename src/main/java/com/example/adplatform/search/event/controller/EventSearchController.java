package com.example.adplatform.search.event.controller;

import com.example.adplatform.common.response.Result;
import com.example.adplatform.search.event.dto.EventSearchRequest;
import com.example.adplatform.search.event.service.EventSearchService;
import com.example.adplatform.search.event.vo.EventSearchPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/search/events")
public class EventSearchController {

    private final EventSearchService eventSearchService;

    @GetMapping
    public Result<EventSearchPageVO> search(
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long slotId,
            @RequestParam(required = false) Long viewerId,
            @RequestParam(required = false) Boolean charged,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return Result.success(eventSearchService.search(new EventSearchRequest(
                eventId, requestId, eventType, planId, materialId, slotId, viewerId, charged,
                startTime, endTime, cursor, size)));
    }
}
