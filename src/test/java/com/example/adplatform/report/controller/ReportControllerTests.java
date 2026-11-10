package com.example.adplatform.report.controller;

import com.example.adplatform.report.request.DailyReportRequest;
import com.example.adplatform.report.request.FunnelReportRequest;
import com.example.adplatform.report.request.TopMaterialsRequest;
import com.example.adplatform.common.exception.GlobalExceptionHandler;
import com.example.adplatform.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTests {

    private static final String PLAN_ID = "plan_00000000000000000000000000000001";
    private final ReportService reports = mock(ReportService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ReportController(reports))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void shouldBindDailyReportDateAndOptionalPlanFromJson() throws Exception {
        mvc.perform(post("/api/report/daily").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statDate\":\"2026-11-09\",\"planPublicId\":\"" + PLAN_ID + "\"}"))
                .andExpect(status().isOk());
        verify(reports).daily(new DailyReportRequest(LocalDate.of(2026, 11, 9), PLAN_ID));
    }

    @Test
    void shouldBindFunnelDatesWithoutPlanFilter() throws Exception {
        mvc.perform(post("/api/report/funnel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2026-11-01\",\"endDate\":\"2026-11-09\"}"))
                .andExpect(status().isOk());
        verify(reports).funnel(new FunnelReportRequest(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 9), null));
    }

    @Test
    void shouldBindTopMaterialsFiltersAndLimit() throws Exception {
        mvc.perform(post("/api/report/top-materials").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"2026-11-01","endDate":"2026-11-09","advertiserPublicId":"%s","limit":5}
                                """.formatted(PLAN_ID)))
                .andExpect(status().isOk());
        verify(reports).topMaterials(new TopMaterialsRequest(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 9), PLAN_ID, 5));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "/api/report/daily|{}",
            "/api/report/daily|{\"statDate\":\"invalid\"}",
            "/api/report/daily|{\"statDate\":\"2026-11-09\",\"advertiserPublicId\":\"bad\"}",
            "/api/report/funnel|{\"startDate\":\"2026-11-01\"}",
            "/api/report/funnel|{\"endDate\":\"2026-11-09\"}",
            "/api/report/top-materials|{\"startDate\":\"2026-11-01\"}",
            "/api/report/top-materials|{\"startDate\":\"2026-11-01\",\"endDate\":\"2026-11-09\",\"advertiserPublicId\":\"bad\"}"
    })
    void shouldRejectMissingDatesAndInvalidFilters(String path, String body) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reports);
    }
}
