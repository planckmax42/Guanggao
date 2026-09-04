package com.example.adplatform.infra.warmup;

import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.port.slot.SlotCachePort;
import com.example.adplatform.infra.bloomfilter.delivery.slot.BloomRebuildResult;
import com.example.adplatform.infra.bloomfilter.delivery.slot.SlotBloomOperationsService;
import com.example.adplatform.infra.bloomfilter.tracking.materialMetadata.MaterialMetadataBloomService;
import com.example.adplatform.infra.elasticsearch.delivery.Candidate.EsProperties;
import com.example.adplatform.infra.elasticsearch.delivery.Candidate.EsIndexManagerServiceImpl;
import com.example.adplatform.infra.redis.delivery.budget.BudgetRedisServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static com.example.adplatform.infra.bloomfilter.delivery.slot.BloomRebuildResult.RebuildStatus.SUCCESS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WarmUpTasksTests {

    @Test
    void shouldReuseBloomSnapshotWhenWarmingSlotRedis() {
        SlotBloomOperationsService bloomFilterService = mock(SlotBloomOperationsService.class);
        when(bloomFilterService.regularRebuild()).thenReturn(new BloomRebuildResult(
                SUCCESS, Optional.of(List.of("HOME_BANNER")), 10_000L));
        SlotCachePort cacheMaintenancePort = mock(SlotCachePort.class);
        SlotWarmUpTask task = new SlotWarmUpTask(bloomFilterService, cacheMaintenancePort);

        task.warmUp();

        verify(bloomFilterService).regularRebuild();
        verify(cacheMaintenancePort).refreshSlotByCode("HOME_BANNER");
    }

    @Test
    void shouldWarmBudgetsForOnlinePlans() {
        PlanEntity plan = new PlanEntity();
        PlanMapper planMapper = mock(PlanMapper.class);
        when(planMapper.selectList(any())).thenReturn(List.of(plan));
        BudgetRedisServiceImpl budgetService = mock(BudgetRedisServiceImpl.class);
        BudgetWarmUpTask task = new BudgetWarmUpTask(planMapper, budgetService);
        LocalDate today = LocalDate.now();

        task.warmUp();

        verify(budgetService).rebuildBudget(plan, today);
    }

    @Test
    void shouldRebuildEventMetadataBloomFilter() {
        MaterialMetadataBloomService bloomFilterService =
                mock(MaterialMetadataBloomService.class);

        new EventMetadataWarmUpTask(bloomFilterService).warmUp();

        verify(bloomFilterService).regularRebuild();
    }

    @Test
    void shouldInitializeElasticsearchWhenEnabled() {
        EsProperties properties = mock(EsProperties.class);
        when(properties.isEnabled()).thenReturn(true);
        EsIndexManagerServiceImpl esIndexManagerServiceImpl = mock(EsIndexManagerServiceImpl.class);

        new ElasticsearchWarmUpTask(properties, esIndexManagerServiceImpl).warmUp();

        verify(esIndexManagerServiceImpl).initialRebuild();
    }

    @Test
    void shouldSkipElasticsearchWhenDisabled() {
        EsProperties properties = mock(EsProperties.class);
        EsIndexManagerServiceImpl esIndexManagerServiceImpl = mock(EsIndexManagerServiceImpl.class);

        new ElasticsearchWarmUpTask(properties, esIndexManagerServiceImpl).warmUp();

        verify(properties).isEnabled();
        verifyNoInteractions(esIndexManagerServiceImpl);
    }

    @Test
    void shouldContinueWhenElasticsearchInitializationFails() {
        EsProperties properties = mock(EsProperties.class);
        when(properties.isEnabled()).thenReturn(true);
        EsIndexManagerServiceImpl esIndexManagerServiceImpl = mock(EsIndexManagerServiceImpl.class);
        doThrow(new IllegalStateException("Elasticsearch unavailable"))
                .when(esIndexManagerServiceImpl).initialRebuild();

        ElasticsearchWarmUpTask task = new ElasticsearchWarmUpTask(
                properties, esIndexManagerServiceImpl);

        assertDoesNotThrow(task::warmUp);
        verify(esIndexManagerServiceImpl).initialRebuild();
    }
}
