package com.example.adplatform.admin.request.material;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageMaterialRequest(
        @Min(1) Long current,
        @Min(1) @Max(100) Long size,
        Integer status) {
}
