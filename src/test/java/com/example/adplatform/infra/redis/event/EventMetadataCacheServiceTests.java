package com.example.adplatform.infra.redis.event;

import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.query.MaterialPlanJoinRow;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.infra.redis.RedisKeyConstants;
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
        when(fixture.values.get(RedisKeyConstants.eventMaterialMetadata(10L)))
                .thenReturn(fixture.objectMapper.writeValueAsString(expected));

        EventMaterialMetadata actual = fixture.service.get(10L);

        assertThat(actual).isEqualTo(expected);
        verify(fixture.materialMapper, never()).selectMaterialPlanById(any());
    }

    @Test
    void shouldLoadMysqlAndPopulateRedisOnCacheMiss() {
        Fixture fixture = fixture();
        when(fixture.values.get(RedisKeyConstants.eventMaterialMetadata(10L))).thenReturn(null);
        when(fixture.materialMapper.selectMaterialPlanById(10L)).thenReturn(row());

        EventMaterialMetadata actual = fixture.service.get(10L);

        assertThat(actual).isEqualTo(metadata());
        verify(fixture.materialMapper).selectMaterialPlanById(10L);
        verify(fixture.bloomFilterManager).put(10L);
        verify(fixture.values).set(
                eq(RedisKeyConstants.eventMaterialMetadata(10L)),
                any(String.class),
                eq(Duration.ofHours(1)));
    }

    @Test
    void shouldReuseValueFilledWhileWaitingForSingleFlightLock() throws Exception {
        Fixture fixture = fixture();
        EventMaterialMetadata expected = metadata();
        when(fixture.values.get(RedisKeyConstants.eventMaterialMetadata(10L)))
                .thenReturn(null, fixture.objectMapper.writeValueAsString(expected));

        EventMaterialMetadata actual = fixture.service.get(10L);

        assertThat(actual).isEqualTo(expected);
        verify(fixture.materialMapper, never()).selectMaterialPlanById(any());
    }

    @Test
    void shouldRejectDefiniteBloomMissWithoutQueryingMysql() {
        Fixture fixture = fixture();
        when(fixture.bloomFilterManager.definitelyNotContains(10L)).thenReturn(true);

        assertThatThrownBy(() -> fixture.service.get(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("广告素材不存在");
        verify(fixture.values, never()).get(anyString());
        verify(fixture.materialMapper, never()).selectMaterialPlanById(any());
    }

    @Test
    void shouldRegisterBloomImmediatelyAndPopulateRedisOnlyAfterTransactionCommit() {
        Fixture fixture = fixture();
        beginTransactionSynchronization();

        fixture.service.refreshAfterCommit(10L, metadata());

        verify(fixture.bloomFilterManager).put(10L);
        verify(fixture.values, never()).set(anyString(), anyString(), any(Duration.class));
        commitSynchronizations();
        verify(fixture.bloomFilterManager, times(1)).put(10L);
        verify(fixture.values).set(
                eq(RedisKeyConstants.eventMaterialMetadata(10L)),
                anyString(),
                eq(Duration.ofHours(1)));
    }

    @Test
    void shouldKeepSafeBloomFalsePositiveButNotRedisValueAfterRollback() {
        Fixture fixture = fixture();
        beginTransactionSynchronization();

        fixture.service.refreshAfterCommit(10L, metadata());

        verify(fixture.bloomFilterManager).put(10L);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(fixture.values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void shouldEvictPlanMetadataOnlyAfterTransactionCommit() {
        Fixture fixture = fixture();
        when(fixture.materialMapper.selectMaterialIdsByPlanId(20L)).thenReturn(List.of(10L, 11L));
        beginTransactionSynchronization();

        fixture.service.evictPlanAfterCommit(20L);

        verify(fixture.redisTemplate, never()).delete(anyCollection());
        commitSynchronizations();
        verify(fixture.redisTemplate).delete(List.of(
                RedisKeyConstants.eventMaterialMetadata(10L),
                RedisKeyConstants.eventMaterialMetadata(11L)));
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
        MaterialIdBloomFilterManager bloomFilterManager = mock(MaterialIdBloomFilterManager.class);
        EventMetadataCacheProperties properties = properties();
        ObjectMapper objectMapper = new ObjectMapper();
        EventMetadataCacheServiceImpl service = new EventMetadataCacheServiceImpl(
                redisTemplate,
                objectMapper,
                materialMapper,
                bloomFilterManager,
                properties,
                new EventMetadataCacheLockManager(properties));
        return new Fixture(service, objectMapper, redisTemplate, values, materialMapper, bloomFilterManager);
    }

    private EventMetadataCacheProperties properties() {
        EventMetadataCacheProperties properties = new EventMetadataCacheProperties();
        properties.setRedisTtl(Duration.ofHours(1));
        properties.setRedisTtlJitter(Duration.ZERO);
        properties.getLock().setStripes(1_024);
        properties.getLock().setReadWaitTimeout(Duration.ofMillis(100));
        return properties;
    }

    private MaterialPlanJoinRow row() {
        MaterialPlanJoinRow row = new MaterialPlanJoinRow();
        row.setMaterialId(10L);
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
        return new EventMaterialMetadata(20L, 30L, 10000L, 1000L, 100L, "CPC");
    }

    private record Fixture(
            EventMetadataCacheServiceImpl service,
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            ValueOperations<String, String> values,
            MaterialMapper materialMapper,
            MaterialIdBloomFilterManager bloomFilterManager) {
    }
}
