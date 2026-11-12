package com.example.adplatform.admin.request.material;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.adplatform.admin.json.AdminIdDeserializer;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import static com.example.adplatform.common.util.StringUtils.blankToNull;


public record UpdateMaterialRequest(
        @NotNull @Positive @JsonDeserialize(using = AdminIdDeserializer.class) Long id,
        @Positive @JsonDeserialize(using = AdminIdDeserializer.class) Long slotId,
        @Size(max = 128) String name,
        @Size(max = 512) String description,
        @Size(max = 512) String imageUrl,
        @Size(max = 512) String landingPageUrl) {

    public UpdateMaterialRequest {
        name = blankToNull(name);
        description = blankToNull(description);
        imageUrl = blankToNull(imageUrl);
        landingPageUrl = blankToNull(landingPageUrl);
    }

    @AssertTrue(message = "请至少提供一个需要修改的字段")
    public boolean isUpdateProvided() {
        return slotId != null || name != null || description != null
                || imageUrl != null || landingPageUrl != null;
    }
}
