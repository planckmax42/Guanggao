package com.example.adplatform.admin.response.slot;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;


public record SlotOperationResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id) {
}
