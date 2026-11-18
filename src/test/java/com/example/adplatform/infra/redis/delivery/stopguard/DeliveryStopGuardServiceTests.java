package com.example.adplatform.infra.redis.delivery.stopguard;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DeliveryStopGuardServiceTests {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> sets = mock(SetOperations.class);
    private final DeliveryStopGuardService service = new DeliveryStopGuardService(redisTemplate);

    @Test
    void shouldWriteAndRemoveIdsInTheirOwnCollections() {
        when(redisTemplate.opsForSet()).thenReturn(sets);

        service.stopPlan(1L);
        service.stopMaterial(2L);
        service.stopSlot(3L);
        service.resumePlan(1L);
        service.resumeMaterial(2L);
        service.resumeSlot(3L);

        verify(sets).add("ad-platform:stopguard:plans", "1");
        verify(sets).add("ad-platform:stopguard:materials", "2");
        verify(sets).add("ad-platform:stopguard:slots", "3");
        verify(sets).remove("ad-platform:stopguard:plans", "1");
        verify(sets).remove("ad-platform:stopguard:materials", "2");
        verify(sets).remove("ad-platform:stopguard:slots", "3");
        verifyNoMoreInteractions(sets);
    }

    @Test
    void shouldReturnOnlyAllowedIdsForEachEntityType() {
        when(redisTemplate.opsForSet()).thenReturn(sets);
        when(sets.isMember("ad-platform:stopguard:plans", "1", "2", "1"))
                .thenReturn(Map.of("1", true, "2", false));
        when(sets.isMember("ad-platform:stopguard:materials", "1", "2"))
                .thenReturn(Map.of("1", false, "2", true));
        when(sets.isMember("ad-platform:stopguard:slots", new Object[]{"3"}))
                .thenReturn(Map.of("3", true));

        assertEquals(Set.of(2L), service.findAllowedPlans(List.of(1L, 2L, 1L)));
        assertEquals(Set.of(1L), service.findAllowedMaterials(List.of(1L, 2L)));
        assertEquals(Set.of(), service.findAllowedSlots(List.of(3L)));
    }

    @Test
    void shouldSkipRedisForEmptyCandidates() {
        assertEquals(Set.of(), service.findAllowedPlans(List.of()));
        assertEquals(Set.of(), service.findAllowedMaterials(List.of()));
        assertEquals(Set.of(), service.findAllowedSlots(List.of()));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void shouldPropagateRedisFailure() {
        when(redisTemplate.opsForSet()).thenReturn(sets);
        when(sets.isMember("ad-platform:stopguard:plans", new Object[]{"1"}))
                .thenThrow(new DataAccessResourceFailureException("redis down"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> service.findAllowedPlans(List.of(1L)));
    }
}
