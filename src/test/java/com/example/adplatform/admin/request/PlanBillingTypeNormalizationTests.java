package com.example.adplatform.admin.request;

import com.example.adplatform.admin.request.plan.CreatePlanRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PlanBillingTypeNormalizationTests {

    @ParameterizedTest
    @CsvSource({"cpc,CPC", "' CPM ',CPM", "' cPa ',CPA", "CPC,CPC"})
    void normalizesBillingTypeBeforeValidation(String input, String expected) {
        var create = createRequest(input);
        var update = updateRequest(input);
        assertThat(create.billingType()).isEqualTo(expected);
        assertThat(update.billingType()).isEqualTo(expected);
        assertThat(createRequest(create.billingType()).billingType()).isEqualTo(expected);
        assertThat(updateRequest(update.billingType()).billingType()).isEqualTo(expected);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(create)).isEmpty();
            assertThat(factory.getValidator().validate(update)).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"xyz", " cpx ", "CP C"})
    void rejectsUnknownBillingTypeThroughFieldValidation(String input) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (Object request : new Object[]{createRequest(input), updateRequest(input)}) {
                assertThat(factory.getValidator().validate(request))
                        .extracting(v -> v.getPropertyPath().toString())
                        .containsExactly("billingType");
            }
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "　"})
    void requiresBillingTypeForCreateButTreatsBlankUpdateAsAbsent(String input) {
        var update = updateRequest(input);
        assertThat(update.billingType()).isNull();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(createRequest(input)))
                    .extracting(v -> v.getPropertyPath().toString())
                    .contains("billingType");
            assertThat(factory.getValidator().validate(update)).isEmpty();
            var emptyUpdate = new UpdatePlanRequest(1L, null,
                    null, null, null, input, null, null);
            assertThat(factory.getValidator().validate(emptyUpdate))
                    .extracting(v -> v.getMessage())
                    .containsExactly("请至少提供一个需要修改的字段");
        }
    }

    private CreatePlanRequest createRequest(String billingType) {
        return new CreatePlanRequest(1L, "计划", 100L, 10L, 1L,
                billingType, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));
    }

    private UpdatePlanRequest updateRequest(String billingType) {
        return new UpdatePlanRequest(1L, "计划",
                null, null, null, billingType, null, null);
    }
}
