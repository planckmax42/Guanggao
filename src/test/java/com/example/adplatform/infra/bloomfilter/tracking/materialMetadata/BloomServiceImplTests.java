package com.example.adplatform.infra.bloomfilter.tracking.materialMetadata;

import com.example.adplatform.admin.mapper.MaterialMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BloomServiceImplTests {

    @Test
    void shouldBypassBloomCheckBeforeFirstSuccessfulRebuild() {
        BloomServiceImpl manager = manager(mock(MaterialMapper.class));

        assertThat(manager.definitelyNotContains(2L)).isFalse();
        assertThat(manager.GetBloomFilterSnapshot().bloomFilterReady()).isFalse();
    }

    @Test
    void shouldRejectMissingIdAfterRebuild() {
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        when(materialMapper.selectAllMaterialIds()).thenReturn(List.of(1L, 3L));
        BloomServiceImpl manager = manager(materialMapper);

        manager.regularRebuild();

        assertThat(manager.definitelyNotContains(2L)).isTrue();
        assertThat(manager.definitelyNotContains(3L)).isFalse();
    }

    @Test
    void shouldRejectMissingIdOutsideRebuiltDataAfterWatermarkRemoval() {
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        when(materialMapper.selectAllMaterialIds()).thenReturn(List.of(1L, 2L, 3L));
        BloomServiceImpl manager = manager(materialMapper);

        manager.regularRebuild();

        assertThat(manager.definitelyNotContains(4L)).isTrue();
    }

    @Test
    void shouldKeepNewIdAddedDuringRebuild() {
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        MaterialMetadataBloomService manager = manager(materialMapper);
        when(materialMapper.selectAllMaterialIds()).thenAnswer(invocation -> {
            manager.addBloomFilter(4L);
            return List.of(1L, 2L, 3L);
        });

        manager.regularRebuild();

        assertThat(manager.definitelyNotContains(4L)).isFalse();
    }

    @Test
    void shouldExposeActualFalsePositiveRateAndResetItAfterRebuild() {
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        when(materialMapper.selectAllMaterialIds()).thenReturn(List.of(1L));
        BloomServiceImpl manager = manager(materialMapper);
        manager.regularRebuild();
        manager.recordDefiniteNotContain();
        manager.recordFalsePositive();

        BloomSnapshot snapshot = manager.GetBloomFilterSnapshot();

        assertThat(snapshot.bloomFilterReady()).isTrue();
        assertThat(snapshot.totalCount()).isEqualTo(2L);
        assertThat(snapshot.actualFalsePositiveRate()).isEqualTo(0.5D);

        manager.regularRebuild();

        assertThat(manager.GetBloomFilterSnapshot().totalCount()).isZero();
        assertThat(manager.GetBloomFilterSnapshot().actualFalsePositiveRate()).isZero();
    }

    @Test
    void shouldExposeGaugeWhenExpansionReachesMaximumCapacity() {
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        when(materialMapper.selectAllMaterialIds()).thenReturn(List.of(1L));
        BloomProperties properties = properties();
        properties.setInitialCapacity(100L);
        properties.setMaxCapacity(200L);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BloomServiceImpl manager = new BloomServiceImpl(properties, materialMapper, meterRegistry);
        manager.regularRebuild();

        manager.expandRebuild();

        assertThat(manager.GetBloomFilterSnapshot().currentCapacity()).isEqualTo(200L);
        assertThat(meterRegistry.get("material.metadata.bloom.capacity.exhausted")
                .gauge().value()).isEqualTo(1D);
    }

    private BloomServiceImpl manager(MaterialMapper materialMapper) {
        return new BloomServiceImpl(
                properties(), materialMapper, new SimpleMeterRegistry());
    }

    private BloomProperties properties() {
        BloomProperties properties =
                new BloomProperties();
        properties.setInitialCapacity(100);
        properties.setFalsePositiveProbability(0.000001D);
        properties.setExpansionFactor(2D);
        properties.setMaxCapacity(1000);
        properties.setExpansionCooldown(Duration.ZERO);
        return properties;
    }
}
