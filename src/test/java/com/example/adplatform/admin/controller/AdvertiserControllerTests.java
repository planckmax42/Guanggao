package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.service.AdvertiserService;
import com.example.adplatform.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdvertiserControllerTests {

    private final AdvertiserService advertiserService = mock(AdvertiserService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AdvertiserController(advertiserService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void shouldQueryPageUsingJsonBody() throws Exception {
        when(advertiserService.pageQuery(2, 20, 1))
                .thenReturn(new PageResponse<>(2, 20, 0, 0, List.of()));

        mockMvc.perform(post("/api/platform/advertisers/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"current":2,"size":20,"name":"广告","status":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current").value(2))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void shouldUseDefaultPaginationWhenFieldsAreOmitted() throws Exception {
        when(advertiserService.pageQuery(1, 10, null))
                .thenReturn(new PageResponse<>(1, 10, 0, 0, List.of()));

        mockMvc.perform(post("/api/platform/advertisers/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"current\":0}", "{\"size\":0}", "{\"size\":101}"})
    void shouldRejectInvalidPagination(String body) throws Exception {
        mockMvc.perform(post("/api/platform/advertisers/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(advertiserService);
    }
}
