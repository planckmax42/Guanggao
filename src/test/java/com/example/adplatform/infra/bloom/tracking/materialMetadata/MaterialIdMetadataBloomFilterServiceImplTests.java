package com.example.adplatform.infra.bloom.tracking.materialMetadata;

import com.example.adplatform.admin.mapper.MaterialMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MaterialIdMetadataBloomFilterServiceImplTests {

    @Test
    void shouldRejectMissingIdInsideLoadedRange() {
        MaterialIdMetadataBloomFilterServiceImpl manager = manager();
        manager.rebuild(() -> List.of(1L, 3L));

        assertThat(manager.definitelyNotContains(2L)).isTrue();
        assertThat(manager.definitelyNotContains(3L)).isFalse();
    }

    @Test
    void shouldLetNewAutoIncrementIdPassWhenLocalFilterIsStale() {
        MaterialIdMetadataBloomFilterServiceImpl manager = manager();
        manager.rebuild(() -> List.of(1L, 2L, 3L));

        assertThat(manager.definitelyNotContains(4L)).isFalse();
    }

    @Test
    void shouldKeepNewIdAddedDuringRebuild() {
        MaterialIdMetadataBloomFilterServiceImpl manager = manager();
        manager.rebuild(() -> {
            manager.addBloomFilter(4L);
            return List.of(1L, 2L, 3L);
        });

        assertThat(manager.definitelyNotContains(4L)).isFalse();
        assertThat(manager.GetBloomFilterSnapshot().maximumLoadedId()).isEqualTo(4L);
    }

    private MaterialIdMetadataBloomFilterServiceImpl manager() {
        return new MaterialIdMetadataBloomFilterServiceImpl(
                properties(), mock(MaterialMapper.class));
    }

    private MaterialMetadataBloomFilterProperties properties() {
        MaterialMetadataBloomFilterProperties properties =
                new MaterialMetadataBloomFilterProperties();
        properties.setExpectedInsertions(100);
        properties.setFalsePositiveProbability(0.000001D);
        properties.setExpansionFactor(2D);
        properties.setMaxExpectedInsertions(1000);
        properties.setExpansionCooldown(Duration.ZERO);
        return properties;
    }
}
