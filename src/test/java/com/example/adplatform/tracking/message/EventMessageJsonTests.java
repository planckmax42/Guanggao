package com.example.adplatform.tracking.message;

import com.example.adplatform.tracking.entity.EventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.JacksonUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventMessageJsonTests {

    private final ObjectMapper objectMapper = JacksonUtils.enhancedObjectMapper();

    @Test
    void shouldKeepWireFormatAndReadLegacyLowercaseEventType() throws Exception {
        EventMessage message = new EventMessage(
                "event-1",
                "request-1",
                EventType.CLICK,
                "mat_00000000000000000000000000000010",
                20L,
                LocalDateTime.of(2026, 7, 17, 12, 0));

        String json = objectMapper.writeValueAsString(message);

        assertTrue(json.contains("\"eventType\":\"CLICK\""));
        assertEquals(message, objectMapper.readValue(json, EventMessage.class));
        assertEquals(
                EventType.CLICK,
                objectMapper.readValue(json.replace("CLICK", "click"), EventMessage.class).eventType());
    }
}
