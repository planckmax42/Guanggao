package com.example.adplatform.delivery.serviceMQ.impl;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.delivery.converter.DeliveryConverter;
import com.example.adplatform.delivery.portMQ.DeliveryBudgetControlPort;
import com.example.adplatform.delivery.portMQ.DeliveryElasticSearchPort;
import com.example.adplatform.delivery.portMQ.DeliveryFrequencyControlPort;
import com.example.adplatform.delivery.portMQ.DeliverySlotCachePort;
import com.example.adplatform.delivery.portMQ.DeliveryStopGuardPort;
import com.example.adplatform.delivery.request.DeliveryRequest;
import com.example.adplatform.delivery.response.AdDeliveryResponse;
import com.example.adplatform.delivery.response.MaterialMetadata;
import com.example.adplatform.delivery.serviceMQ.AdDeliveryService;
import com.example.adplatform.infra.bloomfilter.delivery.slot.SlotBloomOperationsService;
import com.example.adplatform.infra.elasticsearch.QueryCandidateRequest;
import com.example.adplatform.infra.elasticsearch.QueryCandidateResponse;
import com.example.adplatform.infra.redis.delivery.slot.MQ.RedisOperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdDeliveryServiceImplTests {

    private final DeliverySlotCachePort slotCachePort = mock(DeliverySlotCachePort.class);
    private final DeliveryElasticSearchPort elasticSearchPort = mock(DeliveryElasticSearchPort.class);
    private final DeliveryStopGuardPort stopGuardPort = mock(DeliveryStopGuardPort.class);
    private final DeliveryBudgetControlPort budgetControlPort = mock(DeliveryBudgetControlPort.class);
    private final DeliveryFrequencyControlPort frequencyControlPort = mock(DeliveryFrequencyControlPort.class);
    private final MaterialMapper materialMapper = mock(MaterialMapper.class);
    private final IdentifierGenerator identifierGenerator = mock(IdentifierGenerator.class);
    private final AdDeliveryService service = new AdDeliveryServiceImpl(
            identifierGenerator, slotCachePort, elasticSearchPort, mock(SlotBloomOperationsService.class),
            Mappers.getMapper(DeliveryConverter.class), stopGuardPort, budgetControlPort, frequencyControlPort,
            mock(SlotMapper.class), materialMapper);

    private final DeliveryRequest request =
            new DeliveryRequest(1L, "HOME_BANNER", 100L, "CPC", "ANDROID", "ALL", 25, "CN", List.of("news"), 2);
    private final QueryCandidateRequest query =
            new QueryCandidateRequest(10L, 100L, "CPC", "ANDROID", "ALL", 25, "CN");

    @BeforeEach
    void setUp() {
        when(slotCachePort.getSlotIdFromRedis(request.slotCode())).thenReturn(RedisOperationResult.success(10L));
        when(identifierGenerator.nextId(request)).thenReturn(123L);
    }

    @Test
    void filtersByStopGuardBudgetAndFrequencyBeforeApplyingTheSizeLimit() {
        LocalDate today = LocalDate.now();
        when(elasticSearchPort.query(query)).thenReturn(List.of(
                candidate(101L, 21L, 800L),
                candidate(102L, 22L, 700L),
                candidate(103L, 23L, 600L),
                candidate(104L, 24L, 500L),
                candidate(105L, 24L, 400L),
                candidate(106L, 25L, 300L),
                candidate(107L, 25L, 200L)));
        when(stopGuardPort.findAllowedSlots(List.of(10L))).thenReturn(Set.of(10L));
        when(stopGuardPort.findAllowedPlans(List.of(21L, 22L, 23L, 24L, 25L)))
                .thenReturn(Set.of(22L, 23L, 24L, 25L));
        when(stopGuardPort.findAllowedMaterials(List.of(101L, 102L, 103L, 104L, 105L, 106L, 107L)))
                .thenReturn(Set.of(101L, 103L, 104L, 105L, 106L, 107L));
        when(budgetControlPort.findAllowedPlans(List.of(21L, 22L, 23L, 24L, 25L), today))
                .thenReturn(Set.of(21L, 22L, 24L, 25L));
        when(frequencyControlPort.findAllowedMaterials(
                1L, List.of(101L, 102L, 103L, 104L, 105L, 106L, 107L), today, 5))
                .thenReturn(Set.of(101L, 102L, 103L, 105L, 106L, 107L));
        when(materialMapper.selectBatchIds(List.of(105L, 106L, 107L)))
                .thenReturn(List.of(material(107L), material(106L), material(105L)));

        AdDeliveryResponse response = service.delivery(request);

        assertThat(response.deliveryEventId()).isEqualTo(123L);
        assertThat(response.ads()).extracting(MaterialMetadata::materialId).containsExactly("105", "106");
    }

    @Test
    void returnsNoAdsWhenTheRecalledSlotIsStopped() {
        LocalDate today = LocalDate.now();
        when(elasticSearchPort.query(query)).thenReturn(List.of(candidate(101L, 21L, 800L)));
        when(stopGuardPort.findAllowedSlots(List.of(10L))).thenReturn(Set.of());
        when(stopGuardPort.findAllowedPlans(List.of(21L))).thenReturn(Set.of(21L));
        when(stopGuardPort.findAllowedMaterials(List.of(101L))).thenReturn(Set.of(101L));
        when(budgetControlPort.findAllowedPlans(List.of(21L), today)).thenReturn(Set.of(21L));
        when(frequencyControlPort.findAllowedMaterials(1L, List.of(101L), today, 5)).thenReturn(Set.of(101L));

        assertThat(service.delivery(request).ads()).isEmpty();
        verify(budgetControlPort).findAllowedPlans(List.of(21L), today);
        verify(frequencyControlPort).findAllowedMaterials(1L, List.of(101L), today, 5);
        verifyNoInteractions(materialMapper);
    }

    @Test
    void skipsMaterialLookupWhenElasticsearchReturnsNoCandidates() {
        when(elasticSearchPort.query(query)).thenReturn(List.of());

        assertThat(service.delivery(request).ads()).isEmpty();
        verifyNoInteractions(materialMapper);
    }

    @Test
    void returnsNoAdsWhenNoPlanHasAnAvailableBudget() {
        LocalDate today = LocalDate.now();
        when(elasticSearchPort.query(query)).thenReturn(List.of(candidate(101L, 21L, 800L)));
        when(stopGuardPort.findAllowedSlots(List.of(10L))).thenReturn(Set.of(10L));
        when(stopGuardPort.findAllowedPlans(List.of(21L))).thenReturn(Set.of(21L));
        when(stopGuardPort.findAllowedMaterials(List.of(101L))).thenReturn(Set.of(101L));
        when(budgetControlPort.findAllowedPlans(List.of(21L), today)).thenReturn(Set.of());
        when(frequencyControlPort.findAllowedMaterials(1L, List.of(101L), today, 5)).thenReturn(Set.of(101L));

        assertThat(service.delivery(request).ads()).isEmpty();
        verify(frequencyControlPort).findAllowedMaterials(1L, List.of(101L), today, 5);
        verifyNoInteractions(materialMapper);
    }

    private QueryCandidateResponse candidate(Long materialId, Long planId, Long bidPrice) {
        return new QueryCandidateResponse(materialId, 10L, planId, bidPrice, "CPC");
    }

    private MaterialEntity material(Long id) {
        MaterialEntity material = new MaterialEntity();
        material.setId(id);
        material.setName("广告 " + id);
        material.setDescription("广告描述");
        material.setImageUrl("/ad.png");
        material.setLandingPageUrl("/ad");
        return material;
    }
}
