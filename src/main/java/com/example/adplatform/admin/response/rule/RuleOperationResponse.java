package com.example.adplatform.admin.response.rule;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record RuleOperationResponse(@JsonSerialize(using = ToStringSerializer.class) Long id) {
}
