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

        assertThat(manager.definitelyNotContains("mat_2")).isFalse();
        assertThat(manager.GetBloomFilterSnapshot().bloomFilterReady()).isFalse();
    }

    @Test
    void shouldRejectMissingIdAfterRebuild() {
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        when(materialMapper.selectAllMaterialPublicIds()).thenReturn(List.of("mat_1", "mat_3"));
        BloomServiceImpl manager = manager(materialMapper);

        manager.regularRebuild();

        assertThat(manager.definitelyNotContains("mat_2")).isTrue();
        assertThat(manager.definitelyNotContains("mat_3")).isFalse();
    }

    @Test
    void shouldRejectMissingIdOutsideRebuiltDataAfterWatermarkRemoval() {
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        when(materialMapper.selectAllMaterialPublicIds()).thenReturn(List.of("mat_1", "mat_2", "mat_3"));
        BloomServiceImpl manager = manager(materialMapper);

        manager.regularRebuild();

        assertThat(manager.definitelyNotContains("mat_4")).isTrue();
    }

    @Test
    void shouldKeepNewIdAddedDuringRebuild() {
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        MaterialMetadataBloomService manager = manager(materialMapper);
        when(materialMapper.selectAllMaterialPublicIds()).thenAnswer(invocation -> {
            manager.addBloomFilter("mat_4");
            return List.of("mat_1", "mat_2", "mat_3");
        });

        manager.regularRebuild();

        assertThat(manager.definitelyNotContains("mat_4")).isFalse();
    }

    @Test
    void shouldExposeActualFalsePositiveRateAndResetItAfterRebuild() {
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        when(materialMapper.selectAllMaterialPublicIds()).thenReturn(List.of("mat_1"));
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
        when(materialMapper.selectAllMaterialPublicIds()).thenReturn(List.of("mat_1"));
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
