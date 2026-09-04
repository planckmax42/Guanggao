package com.example.adplatform.delivery.service.impl;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.delivery.port.BudgetAvailabilityPort;
import com.example.adplatform.delivery.port.CandidateRecallPort;
import com.example.adplatform.delivery.port.DeliveryStopGuardQueryPort;
import com.example.adplatform.delivery.port.FrequencyControlPort;
import com.example.adplatform.delivery.port.SlotLookupPort;
import com.example.adplatform.delivery.port.SlotLookupResult;
import com.example.adplatform.delivery.request.AdDeliveryRequest;
import com.example.adplatform.delivery.response.AdDeliveryResponse;
import com.example.adplatform.report.mapper.DailyReportMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdDeliveryServiceImplTests {

    @Test
    void shouldReturnSuccessfulNoFillWhenSlotCacheIsUnavailable() {
        SlotLookupPort slotLookup = mock(SlotLookupPort.class);
        CandidateRecallPort candidateRecall = mock(CandidateRecallPort.class);
        when(slotLookup.getEnabledSlotIdByCode("HOME_BANNER"))
                .thenReturn(SlotLookupResult.cacheUnavailable());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AdDeliveryServiceImpl service = service(slotLookup, candidateRecall, registry);

        AdDeliveryResponse response = service.deliver(request());

        assertEquals(0, response.returnedCount());
        assertEquals(List.of(), response.ads());
        assertEquals(1D, registry.counter(
                "ad.delivery.no.fill", "reason", "slot_cache_unavailable").count());
        verifyNoInteractions(candidateRecall);
    }

    @Test
    void shouldKeepNotFoundDistinctFromInfrastructureNoFill() {
        SlotLookupPort slotLookup = mock(SlotLookupPort.class);
        when(slotLookup.getEnabledSlotIdByCode("HOME_BANNER"))
                .thenReturn(SlotLookupResult.notFound());

        assertThrows(BusinessException.class,
                () -> service(slotLookup, mock(CandidateRecallPort.class), new SimpleMeterRegistry())
                        .deliver(request()));
    }

    private AdDeliveryServiceImpl service(
            SlotLookupPort slotLookup,
            CandidateRecallPort candidateRecall,
            SimpleMeterRegistry registry) {
        return new AdDeliveryServiceImpl(
                slotLookup,
                candidateRecall,
                mock(DeliveryStopGuardQueryPort.class),
                mock(BudgetAvailabilityPort.class),
                mock(FrequencyControlPort.class),
                mock(DailyReportMapper.class),
                registry);
    }

    private AdDeliveryRequest request() {
        return new AdDeliveryRequest(
                1L, "HOME_BANNER", "CN", "ANDROID", 25, "UNKNOWN", List.of(), 3);
    }
}
