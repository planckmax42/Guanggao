package com.example.adplatform.admin.response.plan;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record PlanOperationResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id
) {
}
