package com.example.adplatform.infra.redis.tracking.materialMetadata;

import com.example.adplatform.infra.bloomfilter.tracking.materialMetadata.BloomSnapshot;
import com.example.adplatform.infra.bloomfilter.tracking.materialMetadata.MaterialMetadataBloomService;
import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.query.MaterialPlanJoinRow;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.infra.redis.tracking.TrackingRedisKeys;
import com.example.adplatform.tracking.service.EventMaterialMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventMetadataCacheServiceTests {

    private static final String MATERIAL_PUBLIC_ID = "mat_00000000000000000000000000000010";
    private static final String OTHER_MATERIAL_PUBLIC_ID = "mat_00000000000000000000000000000011";

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void shouldReturnRedisValueWithoutQueryingMysql() throws Exception {
        Fixture fixture = fixture();
        EventMaterialMetadata expected = metadata();
        when(fixture.values.get(TrackingRedisKeys.eventMaterialMetadata(MATERIAL_PUBLIC_ID)))
                .thenReturn(fixture.objectMapper.writeValueAsString(expected));

        EventMaterialMetadata actual = fixture.service.get(MATERIAL_PUBLIC_ID);

        assertThat(actual).isEqualTo(expected);
        verify(fixture.materialMapper, never()).selectMaterialPlanByPublicId(any());
    }

    @Test
    void shouldLoadMysqlAndPopulateRedisOnCacheMiss() {
        Fixture fixture = fixture();
        when(fixture.values.get(TrackingRedisKeys.eventMaterialMetadata(MATERIAL_PUBLIC_ID))).thenReturn(null);
        when(fixture.materialMapper.selectMaterialPlanByPublicId(MATERIAL_PUBLIC_ID)).thenReturn(row());

        EventMaterialMetadata actual = fixture.service.get(MATERIAL_PUBLIC_ID);

        assertThat(actual).isEqualTo(metadata());
        verify(fixture.materialMapper).selectMaterialPlanByPublicId(MATERIAL_PUBLIC_ID);
        verify(fixture.bloomFilterManager).addBloomFilter(MATERIAL_PUBLIC_ID);
        verify(fixture.values).set(
                eq(TrackingRedisKeys.eventMaterialMetadata(MATERIAL_PUBLIC_ID)),
                any(String.class),
                eq(Duration.ofHours(1)));
    }

    @Test
    void shouldReuseValueFilledWhileWaitingForSingleFlightLock() throws Exception {
        Fixture fixture = fixture();
        EventMaterialMetadata expected = metadata();
        when(fixture.values.get(TrackingRedisKeys.eventMaterialMetadata(MATERIAL_PUBLIC_ID)))
                .thenReturn(null, fixture.objectMapper.writeValueAsString(expected));

        EventMaterialMetadata actual = fixture.service.get(MATERIAL_PUBLIC_ID);

        assertThat(actual).isEqualTo(expected);
        verify(fixture.materialMapper, never()).selectMaterialPlanByPublicId(any());
    }

    @Test
    void shouldRejectDefiniteBloomMissWithoutQueryingMysql() {
        Fixture fixture = fixture();
        when(fixture.bloomFilterManager.definitelyNotContains(MATERIAL_PUBLIC_ID)).thenReturn(true);

        assertThatThrownBy(() -> fixture.service.get(MATERIAL_PUBLIC_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("广告素材不存在");
        verify(fixture.values, never()).get(anyString());
        verify(fixture.materialMapper, never()).selectMaterialPlanByPublicId(any());
        verify(fixture.bloomFilterManager).recordDefiniteNotContain();
    }

    @Test
    void shouldRecordBloomFalsePositiveWhenMysqlConfirmsMissingMaterial() {
        Fixture fixture = fixture();
        when(fixture.values.get(TrackingRedisKeys.eventMaterialMetadata(MATERIAL_PUBLIC_ID))).thenReturn(null);
        when(fixture.bloomFilterManager.GetBloomFilterSnapshot())
                .thenReturn(new BloomSnapshot(true, 100L, 0L, 0.01D, 0D));

        assertThatThrownBy(() -> fixture.service.get(MATERIAL_PUBLIC_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("广告素材不存在");

        verify(fixture.materialMapper).selectMaterialPlanByPublicId(MATERIAL_PUBLIC_ID);
        verify(fixture.bloomFilterManager).recordFalsePositive();
    }

    @Test
    void shouldRegisterBloomImmediatelyAndPopulateRedisOnlyAfterTransactionCommit() {
        Fixture fixture = fixture();
        beginTransactionSynchronization();

        fixture.service.refreshAfterCommit(MATERIAL_PUBLIC_ID, metadata());

        verify(fixture.bloomFilterManager).addBloomFilter(MATERIAL_PUBLIC_ID);
        verify(fixture.values, never()).set(anyString(), anyString(), any(Duration.class));
        commitSynchronizations();
        verify(fixture.bloomFilterManager, times(1)).addBloomFilter(MATERIAL_PUBLIC_ID);
        verify(fixture.values).set(
                eq(TrackingRedisKeys.eventMaterialMetadata(MATERIAL_PUBLIC_ID)),
                anyString(),
                eq(Duration.ofHours(1)));
    }

    @Test
    void shouldKeepSafeBloomFalsePositiveButNotRedisValueAfterRollback() {
        Fixture fixture = fixture();
        beginTransactionSynchronization();

        fixture.service.refreshAfterCommit(MATERIAL_PUBLIC_ID, metadata());

        verify(fixture.bloomFilterManager).addBloomFilter(MATERIAL_PUBLIC_ID);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(fixture.values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void shouldEvictPlanMetadataOnlyAfterTransactionCommit() {
        Fixture fixture = fixture();
        when(fixture.materialMapper.selectMaterialPublicIdsByPlanId(20L))
                .thenReturn(List.of(MATERIAL_PUBLIC_ID, OTHER_MATERIAL_PUBLIC_ID));
        beginTransactionSynchronization();

        fixture.service.evictPlanAfterCommit(20L);

        verify(fixture.redisTemplate, never()).delete(anyCollection());
        commitSynchronizations();
        verify(fixture.redisTemplate).delete(List.of(
                TrackingRedisKeys.eventMaterialMetadata(MATERIAL_PUBLIC_ID),
                TrackingRedisKeys.eventMaterialMetadata(OTHER_MATERIAL_PUBLIC_ID)));
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void commitSynchronizations() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    private Fixture fixture() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        MaterialMetadataBloomService bloomFilterManager = mock(MaterialMetadataBloomService.class);
        MaterialMetadataRedisProperties properties = properties();
        ObjectMapper objectMapper = new ObjectMapper();
        MaterialMetadataRedisServiceImpl service = new MaterialMetadataRedisServiceImpl(
                redisTemplate,
                objectMapper,
                materialMapper,
                bloomFilterManager,
                properties,
                new MaterialMetadataRedisLock(properties));
        return new Fixture(service, objectMapper, redisTemplate, values, materialMapper, bloomFilterManager);
    }

    private MaterialMetadataRedisProperties properties() {
        MaterialMetadataRedisProperties properties = new MaterialMetadataRedisProperties();
        properties.setRedisTtl(Duration.ofHours(1));
        properties.setRedisTtlJitter(Duration.ZERO);
        properties.setSingleFlightWaitTimeout(Duration.ofMillis(500));
        properties.getLock().setStripes(1_024);
        properties.getLock().setReadWaitTimeout(Duration.ofMillis(100));
        return properties;
    }

    private MaterialPlanJoinRow row() {
        MaterialPlanJoinRow row = new MaterialPlanJoinRow();
        row.setMaterialId(10L);
        row.setMaterialPublicId(MATERIAL_PUBLIC_ID);
        row.setMaterialPlanId(20L);
        row.setSlotId(30L);
        row.setPlanId(20L);
        row.setBudgetTotal(10000L);
        row.setBudgetDaily(1000L);
        row.setBidPrice(100L);
        row.setBillingType("CPC");
        return row;
    }

    private EventMaterialMetadata metadata() {
        return new EventMaterialMetadata(10L, 20L, 30L, 10000L, 1000L, 100L, "CPC");
    }

    private record Fixture(
            MaterialMetadataRedisServiceImpl service,
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            ValueOperations<String, String> values,
            MaterialMapper materialMapper,
            MaterialMetadataBloomService bloomFilterManager) {
    }
}
