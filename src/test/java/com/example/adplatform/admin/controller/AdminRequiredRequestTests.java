package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.request.advertiser.PageAdvertiserRequest;
import com.example.adplatform.admin.request.material.PageMaterialRequest;
import com.example.adplatform.admin.request.plan.*;
import com.example.adplatform.admin.request.rule.PageRuleRequest;
import com.example.adplatform.admin.request.slot.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRequiredRequestTests {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();
    private final ObjectMapper json = new ObjectMapper();

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    static Stream<Class<?>> pageRequests() {
        return Stream.of(PageAdvertiserRequest.class, PageMaterialRequest.class, PagePlanRequest.class,
                PageRuleRequest.class, PageSlotRequest.class);
    }

    @ParameterizedTest
    @MethodSource("pageRequests")
    void paginationMustBeExplicit(Class<?> type) throws Exception {
        for (String body : List.of("{}", "{\"current\":null,\"size\":null}",
                "{\"current\":1}", "{\"size\":10}")) {
            assertThat(VALIDATOR.validate(json.readValue(body, type))).as(type.getSimpleName() + body).isNotEmpty();
        }
        assertThat(VALIDATOR.validate(json.readValue("{\"current\":1,\"size\":10}", type))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"create"})
    void planBillingTypeMustBeExplicit(String action) {
        for (String billing : new String[]{null, "", " "}) {
            assertThat(VALIDATOR.validate(plan(action, billing)))
                    .anyMatch(violation -> violation.getPropertyPath().toString().equals("billingType"));
        }
        assertThat(VALIDATOR.validate(plan(action, "CPC"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"slotCode", "name", "width", "height", "scene"})
    void slotUpdateAllowsOmittedBusinessFields(String field) throws Exception {
        var body = json.readTree("""
                {"id":"1","slotCode":"HOME",
                 "name":"首页","width":100,"height":100,"scene":"APP_HOME"}
                """);
        assertThat(VALIDATOR.validate(json.treeToValue(body, UpdateSlotRequest.class))).isEmpty();
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).remove(field);
        assertThat(VALIDATOR.validate(json.treeToValue(body, UpdateSlotRequest.class)))
                .isEmpty();
    }

    static Stream<Class<?>> updateRequests() {
        return Stream.of(com.example.adplatform.admin.request.advertiser.UpdateAdvertiserRequest.class,
                com.example.adplatform.admin.request.material.UpdateMaterialRequest.class, UpdatePlanRequest.class,
                com.example.adplatform.admin.request.rule.UpdateRuleRequest.class, UpdateSlotRequest.class);
    }

    @ParameterizedTest
    @MethodSource("updateRequests")
    void updateOnlyRequiresId(Class<?> type) throws Exception {
        Object request = json.findAndRegisterModules().readValue("{\"id\":\"1\"}", type);
        assertThat(VALIDATOR.validate(request)).extracting(v -> v.getMessage())
                .containsExactly("请至少提供一个需要修改的字段");
        assertThat(VALIDATOR.validate(json.readValue("{}", type))).isNotEmpty();
    }

    private Object plan(String action, String billingType) {
        LocalDateTime start = LocalDateTime.now();
        return action.equals("create")
                ? new CreatePlanRequest(1L, "计划", 100L, 10L, 1L,
                    billingType, start, start.plusDays(1))
                : new UpdatePlanRequest(1L, "计划", 100L, 10L, 1L,
                    billingType, start, start.plusDays(1));
    }
}
