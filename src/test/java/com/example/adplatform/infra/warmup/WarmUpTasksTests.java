package com.example.adplatform.infra.warmup;

import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.port.SlotCacheMaintenancePort;
import com.example.adplatform.infra.bloom.delivery.slot.SlotBloomFilterManager;
import com.example.adplatform.infra.bloom.delivery.slot.SlotBloomFilterTracker;
import com.example.adplatform.infra.elasticsearch.config.AdElasticsearchProperties;
import com.example.adplatform.infra.elasticsearch.delivery.CandidateIndexManager;
import com.example.adplatform.infra.redis.delivery.budget.BudgetRedisServiceImpl;
import com.example.adplatform.infra.redis.tracking.metadata.EventMetadataCacheServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

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
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectList(any())).thenReturn(List.of(slot));
        SlotBloomFilterManager bloomFilterManager = mock(SlotBloomFilterManager.class);
        when(bloomFilterManager.rebuild(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<List<SlotEntity>> loader = invocation.getArgument(0);
            return Optional.of(loader.get());
        });
        SlotBloomFilterTracker tracker = mock(SlotBloomFilterTracker.class);
        SlotCacheMaintenancePort cacheMaintenancePort = mock(SlotCacheMaintenancePort.class);
        SlotWarmUpTask task = new SlotWarmUpTask(
                slotMapper,
                bloomFilterManager,
                tracker,
                cacheMaintenancePort);

        task.warmUp();

        verify(slotMapper).selectList(any());
        verify(tracker).reset();
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
