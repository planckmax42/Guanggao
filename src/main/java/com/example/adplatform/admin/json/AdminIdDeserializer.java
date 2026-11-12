package com.example.adplatform.admin.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/** 只作用于 admin ID，避免小数截断；正数及必填规则由 Bean Validation 处理。 */
public class AdminIdDeserializer extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return parser.getLongValue();
        }
        if (parser.hasToken(JsonToken.VALUE_STRING)) {
            String value = parser.getText();
            if (value.matches("[0-9]+")) {
                try {
                    return Long.valueOf(value);
                } catch (NumberFormatException ex) {
                    return context.reportInputMismatch(Long.class, "ID 超出 Long 范围");
                }
            }
        }
        return context.reportInputMismatch(Long.class, "ID 必须是十进制整数字符串或整数");
    }
}
