package com.example.adplatform.infra.redis.delivery.frequencyMQ;

import com.example.adplatform.infra.redis.delivery.frequency.frequencyMQ.FrequencyControlService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FrequencyControlServiceTests {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final FrequencyControlService service = new FrequencyControlService(redis);
    private final LocalDate date = LocalDate.of(2026, 11, 18);

    @Test
    @SuppressWarnings("unchecked")
    void returnsMissingAndBelowLimitMaterialsInInputOrderWithoutDuplicates() {
        HashOperations<String, String, String> hash = mock(HashOperations.class);
        when(redis.<String, String>opsForHash()).thenReturn(hash);
        when(hash.multiGet("frequency:user:2026-11-18:1", List.of("10", "20", "30", "40")))
                .thenReturn(Arrays.asList(null, "2", "3", "4"));

        assertThat(service.findAllowedMaterials(1L, List.of(10L, 20L, 30L, 40L, 10L), date, 3))
                .containsExactly(10L, 20L);
    }

    @Test
    void skipsRedisForEmptyInputOrUnlimitedFrequency() {
        assertThat(service.findAllowedMaterials(1L, List.of(), date, 3)).isEmpty();
        assertThat(service.findAllowedMaterials(1L, List.of(10L, 10L, 20L), date, 0))
                .containsExactly(10L, 20L);
        verifyNoInteractions(redis);
    }

    @Test
    void propagatesRedisFailure() {
        when(redis.<String, String>opsForHash()).thenThrow(new IllegalStateException("Redis unavailable"));
        assertThatThrownBy(() -> service.findAllowedMaterials(1L, List.of(10L), date, 3))
                .isInstanceOf(IllegalStateException.class);
    }
}
