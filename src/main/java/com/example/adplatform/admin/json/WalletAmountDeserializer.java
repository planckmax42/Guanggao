package com.example.adplatform.admin.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/** 钱包金额以整数 micro 传入，避免小数在 Bean Validation 前被截断。 */
public class WalletAmountDeserializer extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return parser.getLongValue();
        }
        return context.reportInputMismatch(Long.class, "钱包金额必须是整数，单位为 micro");
    }
}
