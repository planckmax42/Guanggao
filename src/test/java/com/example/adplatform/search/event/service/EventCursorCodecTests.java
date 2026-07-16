package com.example.adplatform.search.event.service;

import com.example.adplatform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventCursorCodecTests {

    private final EventCursorCodec codec = new EventCursorCodec(new ObjectMapper());

    @Test
    void shouldRoundTripTwoSearchAfterValues() {
        String cursor = codec.encode(List.of("2026-07-16T20:30:00", "evt-100"));

        assertThat(codec.decode(cursor)).containsExactly("2026-07-16T20:30:00", "evt-100");
    }

    @Test
    void shouldRejectMalformedOrStructurallyInvalidCursor() {
        assertThatThrownBy(() -> codec.decode("not-base64!"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("事件检索游标无效");

        String oneValueCursor = codec.encode(List.of("only-one-value"));
        assertThatThrownBy(() -> codec.decode(oneValueCursor))
                .isInstanceOf(BusinessException.class)
                .hasMessage("事件检索游标无效");
    }

    @Test
    void shouldReturnEmptySearchAfterForBlankCursor() {
        assertThat(codec.decode(null)).isEmpty();
        assertThat(codec.decode(" ")).isEmpty();
    }
}
