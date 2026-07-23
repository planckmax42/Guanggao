package com.example.adplatform.tracking.controller;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.exception.GlobalExceptionHandler;
import com.example.adplatform.tracking.response.EventResponse;
import com.example.adplatform.tracking.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventControllerTests {

    private EventService eventService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        eventService = mock(EventService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EventController(eventService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnOriginalSuccessBodyAfterAsyncAcknowledgement()
            throws Exception {
        when(eventService.collect(any())).thenReturn(
                CompletableFuture.completedFuture(
                        new EventResponse(
                                "event-1", "IMPRESSION", false, null)));

        MvcResult pending = mockMvc.perform(post("/api/tracking/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.eventId").value("event-1"))
                .andExpect(jsonPath("$.data.eventType").value("IMPRESSION"))
                .andExpect(jsonPath("$.data.duplicate").value(false));
    }

    @Test
    void shouldReturn503WhenAsyncKafkaPublishFails() throws Exception {
        when(eventService.collect(any())).thenReturn(
                CompletableFuture.failedFuture(new BusinessException(
                        ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,
                        "Kafka 暂时不可用，请使用相同 eventId 稍后重试")));

        MvcResult pending = mockMvc.perform(post("/api/tracking/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(
                        ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE.getCode()))
                .andExpect(jsonPath("$.message").value(
                        "Kafka 暂时不可用，请使用相同 eventId 稍后重试"));
    }

    @Test
    void shouldReturn500WhenAsyncKafkaPublishCannotBeRetried()
            throws Exception {
        when(eventService.collect(any())).thenReturn(
                CompletableFuture.failedFuture(new BusinessException(
                        ErrorCode.SYSTEM_ERROR,
                        "广告事件写入 Kafka 失败")));

        MvcResult pending = mockMvc.perform(post("/api/tracking/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(
                        ErrorCode.SYSTEM_ERROR.getCode()));
    }

    @Test
    void shouldKeepRequestValidationOnAsyncEndpoint() throws Exception {
        mockMvc.perform(post("/api/tracking/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "IMPRESSION",
                                  "materialId": 1,
                                  "viewerId": 2
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(
                        ErrorCode.PARAM_VALIDATION_FAILED.getCode()));
    }

    private String requestBody() {
        return """
                {
                  "eventId": "event-1",
                  "requestId": "request-1",
                  "eventType": "IMPRESSION",
                  "materialId": 1,
                  "viewerId": 2,
                  "eventTime": "2026-07-23T12:00:00"
                }
                """;
    }
}
