package com.example.adplatform.admin.request.slot;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.example.adplatform.common.util.PublicIdUtils.SLOT_PATTERN;
import static com.example.adplatform.common.util.StringUtils.blankToNull;

public record UpdateSlotRequest(
        @NotBlank @Pattern(regexp = SLOT_PATTERN) String publicId,
        @Size(max = 64) String slotCode,
        @Size(max = 128) String name,
        @Min(1) @Max(10000) Integer width,
        @Min(1) @Max(10000) Integer height,
        @Size(max = 64) String scene) {

    public UpdateSlotRequest {
        slotCode = blankToNull(slotCode);
        name = blankToNull(name);
        scene = blankToNull(scene);
    }

    @AssertTrue(message = "请至少提供一个需要修改的字段")
    public boolean isUpdateProvided() {
        return slotCode != null || name != null || width != null || height != null || scene != null;
    }
}
