package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.request.material.AuditMaterialRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanRequest;
import com.example.adplatform.admin.request.slot.UpdateSlotRequest;
import com.example.adplatform.admin.request.slot.UpdateSlotStatusRequest;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.service.MaterialService;
import com.example.adplatform.admin.service.PlanService;
import com.example.adplatform.admin.service.RuleService;
import com.example.adplatform.admin.service.SlotService;
import com.example.adplatform.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminRequestBodyTests {

    private static final String PLAN_ID = "plan_00000000000000000000000000000001";
    private static final String SLOT_ID = "slot_00000000000000000000000000000001";
    private static final String MATERIAL_ID = "mat_00000000000000000000000000000001";
    private final PlanService plans = mock(PlanService.class);
    private final SlotService slots = mock(SlotService.class);
    private final MaterialService materials = mock(MaterialService.class);
    private final RuleService rules = mock(RuleService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new PlanController(plans), new SlotController(slots),
                    new MaterialController(materials), new MaterialReviewController(materials),
                    new RuleController(rules))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @ParameterizedTest
    @ValueSource(strings = {"/api/platform/slots/page", "/api/advertiser/plans/page", "/api/advertiser/materials/page"})
    void shouldPreservePaginationDefaults(String path) throws Exception {
        when(slots.pageQuery(1, 10, null, null)).thenReturn(new PageResponse<>(1, 10, 0, 0, List.of()));
        when(plans.pageQuery(1, 10, null, null)).thenReturn(new PageResponse<>(1, 10, 0, 0, List.of()));
        when(materials.pageQuery(1, 10, null, null)).thenReturn(new PageResponse<>(1, 10, 0, 0, List.of()));

        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    void shouldBindPaginationAndFiltersFromJson() throws Exception {
        mvc.perform(post("/api/platform/slots/page").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current\":2,\"size\":20,\"slotCode\":\"home\",\"status\":1}"))
                .andExpect(status().isOk());
        verify(slots).pageQuery(2, 20, "home", 1);

        String advertiserId = "adv_00000000000000000000000000000001";
        mvc.perform(post("/api/advertiser/plans/page").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current\":3,\"size\":30,\"advertiserPublicId\":\"" + advertiserId + "\",\"status\":\"ONLINE\"}"))
                .andExpect(status().isOk());
        verify(plans).pageQuery(3, 30, advertiserId, "ONLINE");

        mvc.perform(post("/api/advertiser/materials/page").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current\":4,\"size\":40,\"planPublicId\":\"" + PLAN_ID + "\",\"auditStatus\":\"APPROVED\"}"))
                .andExpect(status().isOk());
        verify(materials).pageQuery(4, 40, PLAN_ID, "APPROVED");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "/api/platform/slots/page|{\"current\":0}",
            "/api/platform/slots/page|{\"size\":101}",
            "/api/advertiser/plans/page|{\"size\":0}",
            "/api/advertiser/plans/page|{\"advertiserPublicId\":\"bad\"}",
            "/api/advertiser/materials/page|{\"current\":0}",
            "/api/advertiser/materials/page|{\"advertiserPublicId\":\"bad\"}",
            "/api/advertiser/rules/by-plan|{}",
            "/api/advertiser/rules/by-plan|{\"advertiserPublicId\":\"bad\"}"
    })
    void shouldRejectInvalidQueryBodies(String path, String body) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(slots, plans, materials, rules);
    }

    @ParameterizedTest
    @ValueSource(strings = {"online", "pause", "offline"})
    void shouldReadPlanIdFromBodyForStatusChanges(String action) throws Exception {
        mvc.perform(put("/api/advertiser/plans/" + action).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicId\":\"" + PLAN_ID + "\"}"))
                .andExpect(status().isOk());
        switch (action) {
            case "online" -> verify(plans).online(PLAN_ID);
            case "pause" -> verify(plans).pause(PLAN_ID);
            case "offline" -> verify(plans).offline(PLAN_ID);
            default -> throw new AssertionError(action);
        }
    }

    @Test
    void shouldReadPlanIdAndUpdateFieldsFromOneBody() throws Exception {
        mvc.perform(put("/api/advertiser/plans").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"publicId":"%s","name":"更新计划","budgetTotal":1000,"budgetDaily":100,
                                 "bidPrice":10,"billingType":"CPC","startTime":"2026-01-01T00:00:00",
                                 "endTime":"2099-12-31T23:59:59"}
                                """.formatted(PLAN_ID)))
                .andExpect(status().isOk());
        ArgumentCaptor<UpdatePlanRequest> request = ArgumentCaptor.forClass(UpdatePlanRequest.class);
        verify(plans).update(eq(PLAN_ID), request.capture());
        assertThat(request.getValue().name()).isEqualTo("更新计划");
    }

    @Test
    void shouldReadMaterialIdAndAuditStatusFromOneBody() throws Exception {
        mvc.perform(put("/api/platform/materials/audit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicId\":\"" + MATERIAL_ID + "\",\"auditStatus\":\"APPROVED\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<AuditMaterialRequest> request = ArgumentCaptor.forClass(AuditMaterialRequest.class);
        verify(materials).audit(eq(MATERIAL_ID), request.capture());
        assertThat(request.getValue().auditStatus()).isEqualTo("APPROVED");
    }

    @Test
    void shouldUpdateSlotsWithoutPathParameters() throws Exception {
        mvc.perform(put("/api/platform/slots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicId\":\"" + SLOT_ID + "\",\"name\":\"首页\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<UpdateSlotRequest> update = ArgumentCaptor.forClass(UpdateSlotRequest.class);
        verify(slots).update(update.capture());
        assertThat(update.getValue().publicId()).isEqualTo(SLOT_ID);
        assertThat(update.getValue().name()).isEqualTo("首页");

        mvc.perform(put("/api/platform/slots/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicId\":\"" + SLOT_ID + "\",\"slotCode\":\"home\",\"status\":0}"))
                .andExpect(status().isOk());
        verify(slots).updateStatus(new UpdateSlotStatusRequest(SLOT_ID, "home", 0));
    }

    @Test
    void shouldQueryRulesUsingPlanIdFromBody() throws Exception {
        mvc.perform(post("/api/advertiser/rules/by-plan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planPublicId\":\"" + PLAN_ID + "\"}"))
                .andExpect(status().isOk());
        verify(rules).getByPlanPublicId(PLAN_ID);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "/api/advertiser/plans/online|{}",
            "/api/advertiser/plans/pause|{\"publicId\":\"bad\"}",
            "/api/advertiser/plans/offline|{\"publicId\":\"\"}",
            "/api/platform/slots|{\"publicId\":\"bad\",\"name\":\"首页\"}",
            "/api/platform/slots/status|{\"publicId\":\"bad\",\"slotCode\":\"home\",\"status\":1}",
            "/api/platform/materials/audit|{\"auditStatus\":\"APPROVED\"}",
            "/api/platform/materials/audit|{\"publicId\":\"bad\",\"auditStatus\":\"APPROVED\"}"
    })
    void shouldValidateResourceIdsInRequestBodies(String path, String body) throws Exception {
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(slots, plans, materials, rules);
    }
}
