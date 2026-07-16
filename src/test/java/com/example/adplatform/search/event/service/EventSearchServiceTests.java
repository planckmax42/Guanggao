package com.example.adplatform.search.event.service;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.search.config.AdElasticsearchProperties;
import com.example.adplatform.search.event.dto.EventSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventSearchServiceTests {

    private EventSearchService service;

    @BeforeEach
    void setUp() {
        AdElasticsearchProperties properties = new AdElasticsearchProperties();
        service = new EventSearchService(
                properties,
                null,
                null,
                new EventCursorCodec(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    @Test
    void shouldRejectReversedTimeRangeBeforeQueryingElasticsearch() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 16, 12, 0);
        EventSearchRequest request = request(start, start.minusMinutes(1), null, 20);

        assertThatThrownBy(() -> service.search(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("结束时间不能早于开始时间");
    }

    @Test
    void shouldRejectRangesLongerThanConfiguredMaximum() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        EventSearchRequest request = request(start, start.plusDays(32), null, 20);

        assertThatThrownBy(() -> service.search(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过31天");
    }

    @Test
    void shouldRejectInvalidCursorBeforeQueryingElasticsearch() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 16, 12, 0);
        EventSearchRequest request = request(start, start.plusMinutes(1), "bad!", 20);

        assertThatThrownBy(() -> service.search(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("事件检索游标无效");
    }

    @Test
    void shouldConvertLocalSearchBoundaryToEpochMillisInApplicationTimeZone() {
        LocalDateTime boundary = LocalDateTime.of(2026, 7, 16, 12, 0);

        assertThat(EventSearchService.toEpochMillis(boundary))
                .isEqualTo(boundary.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    private EventSearchRequest request(
            LocalDateTime start,
            LocalDateTime end,
            String cursor,
            Integer size) {
        return new EventSearchRequest(
                null, null, null, null, null, null, null, null,
                start, end, cursor, size);
    }
}
