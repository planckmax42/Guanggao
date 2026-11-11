package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.request.advertiser.PageAdvertiserRequest;
import com.example.adplatform.admin.request.material.PageMaterialRequest;
import com.example.adplatform.admin.request.plan.PagePlanRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanStatusRequest;
import com.example.adplatform.admin.request.rule.PageRuleRequest;
import com.example.adplatform.admin.request.slot.PageSlotRequest;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.service.*;
import com.example.adplatform.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminRequestBodyTests {
    private final AdvertiserService advertisers = mock(AdvertiserService.class);
    private final PlanService plans = mock(PlanService.class);
    private final SlotService slots = mock(SlotService.class);
    private final MaterialService materials = mock(MaterialService.class);
    private final RuleService rules = mock(RuleService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new AdvertiserController(advertisers), new PlanController(plans), new SlotController(slots),
                    new MaterialController(materials), new RuleController(rules))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @ParameterizedTest
    @ValueSource(strings = {"/api/platform/advertisers", "/api/platform/slots", "/api/advertiser/plans",
            "/api/advertiser/materials", "/api/advertiser/rules"})
    void shouldQueryExplicitPageWithoutFilters(String path) throws Exception {
        when(advertisers.pageQuery(new PageAdvertiserRequest(1L, 10L))).thenReturn(new PageResponse<>(1, 10, 0, 0, List.of()));
        when(slots.pageQuery(new PageSlotRequest(1L, 10L))).thenReturn(new PageResponse<>(1, 10, 0, 0, List.of()));
        when(plans.pageQuery(new PagePlanRequest(1L, 10L))).thenReturn(new PageResponse<>(1, 10, 0, 0, List.of()));
        when(materials.pageQuery(new PageMaterialRequest(1L, 10L))).thenReturn(new PageResponse<>(1, 10, 0, 0, List.of()));
        when(rules.pageQuery(new PageRuleRequest(1L, 10L))).thenReturn(new PageResponse<>(1, 10, 0, 0, List.of()));
        mvc.perform(post(path + "/page").contentType(MediaType.APPLICATION_JSON).content("{\"current\":1,\"size\":10}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/platform/advertisers", "/api/platform/slots", "/api/advertiser/plans",
            "/api/advertiser/materials", "/api/advertiser/rules"})
    void shouldRejectInvalidPaginationAndDeleteId(String path) throws Exception {
        for (String body : List.of("{}", "{\"current\":null,\"size\":10}", "{\"current\":1,\"size\":null}", "{\"current\":1}", "{\"size\":10}", "{\"current\":0,\"size\":10}", "{\"current\":1,\"size\":101}")) {
            mvc.perform(post(path + "/page").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(delete(path + "/delete").contentType(MediaType.APPLICATION_JSON).content("{\"publicId\":\"bad\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(advertisers, slots, plans, materials, rules);
    }

    @ParameterizedTest
    @CsvSource({"/api/platform/advertisers,adv", "/api/platform/slots,slot", "/api/advertiser/plans,plan",
            "/api/advertiser/materials,mat", "/api/advertiser/rules,rule"})
    void shouldExposeDeleteWithPublicIdOnly(String path, String prefix) throws Exception {
        mvc.perform(delete(path + "/delete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicId\":\"" + prefix + "_00000000000000000000000000000001\"}"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"online", "pause", "offline"})
    void shouldKeepPlanLifecycleEndpoints(String action) throws Exception {
        String id = "plan_00000000000000000000000000000001";
        mvc.perform(put("/api/advertiser/plans/" + action).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicId\":\"" + id + "\"}"))
                .andExpect(status().isOk());
        switch (action) {
            case "online" -> verify(plans).online(new UpdatePlanStatusRequest(id));
            case "pause" -> verify(plans).pause(new UpdatePlanStatusRequest(id));
            case "offline" -> verify(plans).offline(new UpdatePlanStatusRequest(id));
        }
    }
}
