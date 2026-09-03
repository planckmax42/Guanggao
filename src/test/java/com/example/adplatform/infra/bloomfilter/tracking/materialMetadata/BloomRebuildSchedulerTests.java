package com.example.adplatform.infra.bloomfilter.tracking.materialMetadata;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.example.adplatform.infra.bloomfilter.tracking.materialMetadata.BloomRebuildResult.RebuildStatus.SUCCESS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BloomRebuildSchedulerTests {

    @Test
    void shouldNotExpandBeforeBloomFilterIsReady() {
        MaterialMetadataBloomService service = mock(MaterialMetadataBloomService.class);
        BloomProperties properties = properties();
        when(service.GetBloomFilterSnapshot())
                .thenReturn(new BloomSnapshot(false, 100L, 10L, 0.1D, 0.1D));

        new BloomRebuildScheduler(service, properties).expandWhenNeeded();

        verify(service, never()).expandRebuild();
    }

    @Test
    void shouldNotExpandWhenActualFalsePositiveRateIsBelowThreshold() {
        MaterialMetadataBloomService service = mock(MaterialMetadataBloomService.class);
        BloomProperties properties = properties();
        when(service.GetBloomFilterSnapshot())
                .thenReturn(new BloomSnapshot(true, 100L, 10L, 0.1D, 0.001D));

        new BloomRebuildScheduler(service, properties).expandWhenNeeded();

        verify(service, never()).expandRebuild();
    }

    @Test
    void shouldExpandWhenActualAndEstimatedRatesReachThreshold() {
        MaterialMetadataBloomService service = mock(MaterialMetadataBloomService.class);
        BloomProperties properties = properties();
        when(service.GetBloomFilterSnapshot())
                .thenReturn(new BloomSnapshot(true, 100L, 10L, 0.1D, 0.1D));
        when(service.expandRebuild())
                .thenReturn(new BloomRebuildResult(SUCCESS, Optional.empty(), 200L));

        new BloomRebuildScheduler(service, properties).expandWhenNeeded();

        verify(service).expandRebuild();
    }

    private BloomProperties properties() {
        BloomProperties properties = new BloomProperties();
        properties.setFalsePositiveProbability(0.01D);
        return properties;
    }
}
