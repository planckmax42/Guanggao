package com.example.adplatform.admin.request;

import com.example.adplatform.admin.request.advertiser.UpdateAdvertiserRequest;
import com.example.adplatform.admin.request.material.UpdateMaterialRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateRequestNormalizationTests {
    @Test
    void normalizesAllOptionalBlankStringsBeforeValidation() {
        var advertiser = new UpdateAdvertiserRequest("adv_" + "a".repeat(32), "", " ", "\t", "\n");
        var material = new UpdateMaterialRequest("mat_" + "a".repeat(32), " ", "", "\n", "\t", "　");
        var plan = new UpdatePlanRequest("plan_" + "a".repeat(32), " ", null, null, null, "\t", null, null);
        assertThat(advertiser.name()).isNull();
        assertThat(advertiser.industry()).isNull();
        assertThat(advertiser.contactName()).isNull();
        assertThat(advertiser.contactEmail()).isNull();
        assertThat(material.slotPublicId()).isNull();
        assertThat(material.name()).isNull();
        assertThat(material.description()).isNull();
        assertThat(material.imageUrl()).isNull();
        assertThat(material.landingPageUrl()).isNull();
        assertThat(plan.name()).isNull();
        assertThat(plan.billingType()).isNull();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (Object request : new Object[]{advertiser, material, plan}) {
                assertThat(factory.getValidator().validate(request))
                        .extracting(v -> v.getMessage())
                        .containsExactly("请至少提供一个需要修改的字段");
            }
        }
    }

    @Test
    void preservesNonblankContentAndAllowsMixedUpdates() {
        var advertiser = new UpdateAdvertiserRequest("adv_" + "a".repeat(32), " 名称 ", null, " ", "");
        var material = new UpdateMaterialRequest("mat_" + "a".repeat(32), " ", " 名称 ", null, "", "\t");
        var plan = new UpdatePlanRequest("plan_" + "a".repeat(32), " ", null, 10L, null, "", null, null);
        assertThat(advertiser.name()).isEqualTo(" 名称 ");
        assertThat(material.name()).isEqualTo(" 名称 ");
        assertThat(plan.budgetDaily()).isEqualTo(10L);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (Object request : new Object[]{advertiser, material, plan}) {
                assertThat(factory.getValidator().validate(request)).isEmpty();
            }
        }
    }

    @Test
    void keepsPublicIdRequiredAndRejectsInvalidNonblankValues() {
        var advertiser = new UpdateAdvertiserRequest(" ", "名称", null, null, "invalid-email");
        var material = new UpdateMaterialRequest(" ", "invalid-slot", "名称", null, null, null);
        var plan = new UpdatePlanRequest(" ", "名称", null, null, null, "INVALID", null, null);
        assertThat(advertiser.publicId()).isEqualTo(" ");
        assertThat(material.publicId()).isEqualTo(" ");
        assertThat(plan.publicId()).isEqualTo(" ");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(advertiser))
                    .extracting(v -> v.getPropertyPath().toString()).contains("publicId", "contactEmail");
            assertThat(factory.getValidator().validate(material))
                    .extracting(v -> v.getPropertyPath().toString()).contains("publicId", "slotPublicId");
            assertThat(factory.getValidator().validate(plan))
                    .extracting(v -> v.getPropertyPath().toString()).contains("publicId", "billingType");
        }
    }
}
