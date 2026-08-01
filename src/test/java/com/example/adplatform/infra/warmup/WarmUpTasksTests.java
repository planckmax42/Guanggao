package com.example.adplatform.infra.warmup;

import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.port.SlotCacheMaintenancePort;
import com.example.adplatform.infra.bloom.delivery.slot.SlotBloomFilterService;
import com.example.adplatform.infra.elasticsearch.config.AdElasticsearchProperties;
import com.example.adplatform.infra.elasticsearch.delivery.CandidateIndexManager;
import com.example.adplatform.infra.redis.delivery.budget.BudgetRedisServiceImpl;
import com.example.adplatform.infra.redis.tracking.metadata.EventMetadataCacheServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WarmUpTasksTests {

    @Test
    void shouldReuseBloomSnapshotWhenWarmingSlotRedis() {
        SlotEntity slot = new SlotEntity();
        slot.setSlotCode("HOME_BANNER");
        SlotBloomFilterService bloomFilterService = mock(SlotBloomFilterService.class);
        when(bloomFilterService.rebuildWithSnapshot()).thenReturn(Optional.of(List.of(slot)));
        SlotCacheMaintenancePort cacheMaintenancePort = mock(SlotCacheMaintenancePort.class);
        SlotWarmUpTask task = new SlotWarmUpTask(bloomFilterService, cacheMaintenancePort);

        task.warmUp();

        verify(bloomFilterService).rebuildWithSnapshot();
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
        EventMetadataCacheServiceImpl cacheService = mock(EventMetadataCacheServiceImpl.class);

        new EventMetadataWarmUpTask(cacheService).warmUp();

        verify(cacheService).rebuildBloomFilter();
    }

    @Test
    void shouldBuildElasticsearchIndexOnlyWhenEnabledAndAliasIsMissing() {
        AdElasticsearchProperties properties = mock(AdElasticsearchProperties.class);
        when(properties.isEnabled()).thenReturn(true);
        CandidateIndexManager indexManager = mock(CandidateIndexManager.class);
        when(indexManager.aliasExists()).thenReturn(false);

        new ElasticsearchWarmUpTask(properties, indexManager).warmUp();

        verify(indexManager).rebuild();
    }

    @Test
    void shouldSkipElasticsearchWhenDisabled() {
        AdElasticsearchProperties properties = mock(AdElasticsearchProperties.class);
        CandidateIndexManager indexManager = mock(CandidateIndexManager.class);

        new ElasticsearchWarmUpTask(properties, indexManager).warmUp();

        verify(properties).isEnabled();
        verifyNoInteractions(indexManager);
    }

    @Test
    void shouldKeepCurrentElasticsearchIndexWhenAliasExists() {
        AdElasticsearchProperties properties = mock(AdElasticsearchProperties.class);
        when(properties.isEnabled()).thenReturn(true);
        CandidateIndexManager indexManager = mock(CandidateIndexManager.class);
        when(indexManager.aliasExists()).thenReturn(true);

        new ElasticsearchWarmUpTask(properties, indexManager).warmUp();

        verify(indexManager).aliasExists();
        verify(indexManager, never()).rebuild();
    }
}
