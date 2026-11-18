package com.example.adplatform.delivery.serviceMQ.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.delivery.converter.DeliveryConverter;
import com.example.adplatform.delivery.portMQ.*;
import com.example.adplatform.delivery.request.DeliveryRequest;
import com.example.adplatform.delivery.response.AdDeliveryResponse;
import com.example.adplatform.delivery.response.MaterialMetadata;
import com.example.adplatform.delivery.serviceMQ.AdDeliveryService;
import com.example.adplatform.infra.bloomfilter.delivery.slot.SlotBloomOperationsService;
import com.example.adplatform.infra.elasticsearch.QueryCandidateRequest;
import com.example.adplatform.infra.elasticsearch.QueryCandidateResponse;
import com.example.adplatform.infra.redis.delivery.slot.MQ.RedisOperationResult;
import com.example.adplatform.infra.redis.delivery.slot.MQ.RedisOperationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdDeliveryServiceImpl implements AdDeliveryService {

    private static final int MAX_FREQUENCY_PER_USER_DAY = 5;

    private final IdentifierGenerator identifierGenerator;
    private final DeliverySlotCachePort slotCachePort;
    private final DeliveryElasticSearchPort elasticSearchPort;
    private final SlotBloomOperationsService slotBloomOperationsService;
    private final DeliveryConverter deliveryConverter;
    private final DeliveryStopGuardPort stopGuardPort;
    private final DeliveryBudgetControlPort budgetControlPort;
    private final DeliveryFrequencyControlPort frequencyControlPort;
    private final SlotMapper slotMapper;
    private final MaterialMapper materialMapper;

    @Override
    public AdDeliveryResponse delivery(DeliveryRequest request) {
        if(stopGuardPort.isSlotStopped(request.slotCode()))
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"广告位已停用");
        if (slotBloomOperationsService.definiteNotContain(request.slotCode()))
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"广告位不存在");
        Long slotId;
        RedisOperationResult readResult = slotCachePort.getSlotIdFromRedis(request.slotCode());
        RedisOperationStatus status = readResult.status();
        if (status==RedisOperationStatus.CACHE_UNFOUND){
            SlotEntity entity = slotMapper.selectOne(new LambdaQueryWrapper<SlotEntity>()
                    .eq(SlotEntity::getSlotCode, request.slotCode())
                    .eq(SlotEntity::getStatus, CommonStatus.ENABLED));
            if(entity==null){
                //记录误报
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"广告位不存在");
            }
            slotCachePort.writeSlotIdFromRedis(request.slotCode(),entity.getId());
            slotId=entity.getId();
        }
        else slotId = readResult.slotId();
        QueryCandidateRequest candidateRequest = deliveryConverter.deliveryToCandidateRequest(request, slotId);
        List<QueryCandidateResponse> candidates = elasticSearchPort.query(candidateRequest);
        LocalDate today = LocalDate.now();
        List<Long> slotIds = candidates.stream().map(QueryCandidateResponse::slotId).distinct().toList();
        List<Long> planIds = candidates.stream().map(QueryCandidateResponse::planId).distinct().toList();
        List<Long> materialIds = candidates.stream().map(QueryCandidateResponse::materialId).distinct().toList();
        Set<Long> allowedSlots = stopGuardPort.findAllowedSlots(slotIds);
        Set<Long> allowedPlans = stopGuardPort.findAllowedPlans(planIds);
        Set<Long> allowedMaterials = stopGuardPort.findAllowedMaterials(materialIds);
        Set<Long> fundedPlans = budgetControlPort.findAllowedPlans(planIds, today);
        Set<Long> withinFrequency = frequencyControlPort.findAllowedMaterials(request.userId(), materialIds, today, MAX_FREQUENCY_PER_USER_DAY);
        List<Long> result = candidates.stream()
                .filter(candidate ->
                        allowedSlots.contains(candidate.slotId())
                                && allowedPlans.contains(candidate.planId())
                                && allowedMaterials.contains(candidate.materialId())
                                && fundedPlans.contains(candidate.planId())
                                && withinFrequency.contains(candidate.materialId()))
                .map(QueryCandidateResponse::materialId).distinct().toList();
        long deliveryEventId = identifierGenerator.nextId(request).longValue();
        Map<Long, MaterialEntity> materialsById = materialMapper.selectBatchIds(materialIds).stream()
                .collect(Collectors.toMap(MaterialEntity::getId, Function.identity()));
        List<MaterialMetadata> ads = materialIds.stream()
                .map(materialsById::get)
                .filter(Objects::nonNull)
                .limit(request.size())
                .map(material -> new MaterialMetadata(material.getId().toString(), material.getName(),
                        material.getDescription(), material.getImageUrl(), material.getLandingPageUrl()))
                .toList();
        return new AdDeliveryResponse(deliveryEventId, ads);
    }

}
