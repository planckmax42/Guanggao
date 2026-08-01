package com.example.adplatform.infra.bloom.tracking.material;

import com.example.adplatform.infra.redis.tracking.metadata.EventMetadataCacheProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialIdBloomFilterManagerTests {

    @Test
    void shouldRejectMissingIdInsideLoadedRange() {
        MaterialIdBloomFilterManager manager = new MaterialIdBloomFilterManager(properties());
        manager.rebuild(() -> List.of(1L, 3L));

        assertThat(manager.definitelyNotContains(2L)).isTrue();
        assertThat(manager.definitelyNotContains(3L)).isFalse();
    }

    @Test
    void shouldLetNewAutoIncrementIdPassWhenLocalFilterIsStale() {
        MaterialIdBloomFilterManager manager = new MaterialIdBloomFilterManager(properties());
        manager.rebuild(() -> List.of(1L, 2L, 3L));

        assertThat(manager.definitelyNotContains(4L)).isFalse();
    }

    @Test
    void shouldKeepNewIdAddedDuringRebuild() {
        MaterialIdBloomFilterManager manager = new MaterialIdBloomFilterManager(properties());
        manager.rebuild(() -> {
            manager.put(4L);
            return List.of(1L, 2L, 3L);
        });

        assertThat(manager.definitelyNotContains(4L)).isFalse();
        assertThat(manager.status().maximumLoadedId()).isEqualTo(4L);
    }

    private EventMetadataCacheProperties properties() {
        EventMetadataCacheProperties properties = new EventMetadataCacheProperties();
        EventMetadataCacheProperties.Bloom bloom = properties.getBloom();
        bloom.setExpectedInsertions(100);
        bloom.setFalsePositiveProbability(0.000001D);
        bloom.setExpansionFactor(2D);
        bloom.setMaxExpectedInsertions(1000);
        bloom.setExpansionCooldown(Duration.ZERO);
        return properties;
    }
}
