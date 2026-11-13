package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.request.advertiserassistant.*;
import com.example.adplatform.admin.request.advertiserwallet.*;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.response.advertiserassistant.*;
import com.example.adplatform.admin.response.advertiserwallet.*;
import com.example.adplatform.admin.service.AdvertiserAssistantService;
import com.example.adplatform.admin.service.AdvertiserWalletService;
import com.example.adplatform.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdvertiserAccountControllerTests {
    private static final long ID = 1987654321098765432L;
    private static final String ASSISTANTS = "/api/advertiser/assistants";
    private static final String WALLETS = "/api/platform/advertiser-wallets";
    private final AdvertiserAssistantService assistants = mock(AdvertiserAssistantService.class);
    private final AdvertiserWalletService wallets = mock(AdvertiserWalletService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new AdvertiserAssistantController(assistants), new AdvertiserWalletController(wallets))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void shouldCreateAssistantWithoutPasswordAndReturnStringId() throws Exception {
        when(assistants.create(new CreateAdvertiserAssistantRequest(ID, "operator", "运营", "OPERATOR")))
                .thenReturn(new AdvertiserAssistantOperationResponse(ID));
        mvc.perform(post(ASSISTANTS + "/create").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"advertiserId":"1987654321098765432","username":"operator","name":"运营","role":"OPERATOR"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(Long.toString(ID)));
    }

    @Test
    void shouldCreateWalletAndExposeAmountsAsNumbers() throws Exception {
        when(wallets.create(new CreateAdvertiserWalletRequest(ID)))
                .thenReturn(new AdvertiserWalletOperationResponse(ID));
        mvc.perform(post(WALLETS + "/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"advertiserId\":\"1987654321098765432\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(Long.toString(ID)));
        when(wallets.pageQuery(new PageAdvertiserWalletRequest(1L, 10L)))
                .thenReturn(new PageResponse<>(1, 10, 1, 1, List.of(
                        new AdvertiserWalletQueryResponse(ID, ID, 500L, 20L))));
        mvc.perform(post(WALLETS + "/page").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current\":1,\"size\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].advertiserId").value(Long.toString(ID)))
                .andExpect(jsonPath("$.data.records[0].balanceMicro").isNumber())
                .andExpect(jsonPath("$.data.records[0].balanceMicro").value(500))
                .andExpect(jsonPath("$.data.records[0].frozenMicro").value(20));
    }

    @Test
    void shouldPageAssistantsWithoutPasswordFields() throws Exception {
        when(assistants.pageQuery(new PageAdvertiserAssistantRequest(1L, 10L)))
                .thenReturn(new PageResponse<>(1, 10, 1, 1, List.of(
                        new AdvertiserAssistantQueryResponse(ID, ID, "operator", "运营", "OPERATOR", 1))));
        mvc.perform(post(ASSISTANTS + "/page").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current\":1,\"size\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(Long.toString(ID)))
                .andExpect(jsonPath("$.data.records[0].username").value("operator"))
                .andExpect(jsonPath("$.data.records[0].password").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].passwordHash").doesNotExist());
    }

    @Test
    void shouldRoutePartialUpdatesAndDeletes() throws Exception {
        when(assistants.update(new UpdateAdvertiserAssistantRequest(ID, null, "新名称", null)))
                .thenReturn(new AdvertiserAssistantOperationResponse(ID));
        mvc.perform(put(ASSISTANTS + "/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"1987654321098765432\",\"name\":\"新名称\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(Long.toString(ID)));
        when(wallets.update(new UpdateAdvertiserWalletRequest(ID, 0L, null)))
                .thenReturn(new AdvertiserWalletOperationResponse(ID));
        mvc.perform(put(WALLETS + "/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"1987654321098765432\",\"balanceMicro\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(Long.toString(ID)));
        when(assistants.delete(new DeleteAdvertiserAssistantRequest(ID)))
                .thenReturn(new AdvertiserAssistantOperationResponse(ID));
        when(wallets.delete(new DeleteAdvertiserWalletRequest(ID)))
                .thenReturn(new AdvertiserWalletOperationResponse(ID));
        for (String path : List.of(ASSISTANTS, WALLETS)) {
            mvc.perform(delete(path + "/delete").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"1987654321098765432\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(Long.toString(ID)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"current\":1}", "{\"current\":0,\"size\":10}", "{\"current\":1,\"size\":101}"})
    void shouldRejectInvalidPagination(String body) throws Exception {
        for (String path : List.of(ASSISTANTS, WALLETS)) {
            mvc.perform(post(path + "/page").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(assistants, wallets);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "0", "-1", "1.5", "true", "\"adv_old\"", "\"9223372036854775808\""})
    void shouldRejectInvalidIds(String id) throws Exception {
        for (String path : List.of(ASSISTANTS, WALLETS)) {
            mvc.perform(delete(path + "/delete").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":" + id + "}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(assistants, wallets);
    }

    @Test
    void shouldRejectEmptyUpdatesAndNegativeMoney() throws Exception {
        mvc.perform(put(ASSISTANTS + "/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
        for (String body : List.of("{\"id\":1}", "{\"id\":1,\"balanceMicro\":-1}",
                "{\"id\":1,\"frozenMicro\":-1}")) {
            mvc.perform(put(WALLETS + "/update").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(assistants, wallets);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.5", "1.5", "true", "\"100\"", "9223372036854775808"})
    void shouldRejectNonIntegerOrOverflowingWalletAmounts(String amount) throws Exception {
        for (String field : List.of("balanceMicro", "frozenMicro")) {
            mvc.perform(put(WALLETS + "/update").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":1,\"" + field + "\":" + amount + "}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(wallets);
    }

    @Test
    void shouldRejectMissingCreateFieldsAndOversizedUsername() throws Exception {
        mvc.perform(post(WALLETS + "/create").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(ASSISTANTS + "/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"advertiserId\":1,\"name\":\"运营\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(ASSISTANTS + "/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"advertiserId\":1,\"username\":\"" + "x".repeat(65)
                                + "\",\"name\":\"运营\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(assistants, wallets);
    }
}
