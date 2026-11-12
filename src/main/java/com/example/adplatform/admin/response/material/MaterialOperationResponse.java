package com.example.adplatform.admin.response.material;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record MaterialOperationResponse(
    @JsonSerialize(using = ToStringSerializer.class) Long id
) { }
