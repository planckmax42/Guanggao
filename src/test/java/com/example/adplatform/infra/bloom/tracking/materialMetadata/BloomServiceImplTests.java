package com.example.adplatform.infra.bloom.tracking.materialMetadata;

import com.example.adplatform.admin.mapper.MaterialMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BloomServiceImplTests {

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

    private BloomServiceImpl manager(MaterialMapper materialMapper) {
        return new BloomServiceImpl(
                properties(), materialMapper);
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
