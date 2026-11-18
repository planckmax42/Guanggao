package com.example.adplatform.delivery.serviceMQ.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.entity.UserEntity;
import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.mapper.UserMapper;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.delivery.converter.DeliveryConverter;
import com.example.adplatform.delivery.portMQ.*;
import com.example.adplatform.delivery.request.DeliveryRequest;
import com.example.adplatform.delivery.response.AdDeliveryResponse;
import com.example.adplatform.delivery.serviceMQ.AdDeliveryService;
import com.example.adplatform.infra.bloomfilter.delivery.slot.SlotBloomOperationsService;
import com.example.adplatform.infra.elasticsearch.QueryCandidateRequest;
import com.example.adplatform.infra.elasticsearch.QueryCandidateResponse;
import com.example.adplatform.infra.redis.delivery.PlanPrice.PlanPrice;
import com.example.adplatform.infra.redis.delivery.materialMetadata.MaterialMetadata;
import com.example.adplatform.infra.redis.delivery.slot.MQ.RedisOperationResult;
import com.example.adplatform.infra.redis.delivery.slot.MQ.RedisOperationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
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
    private final DeliveryPlanPriceCachePort planPriceCachePort;
    private final DeliveryMetadataCachePort metadataCachePort;
    private final SlotMapper slotMapper;
    private final UserMapper userMapper;
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
        else slotId = readResult.slotId();//频控 预算控制 stopGuard用hash还是json?
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
        List<String> userTags = (List<String>) userMapper.selectOne(new QueryWrapper<UserEntity>().select("tag").eq("id",request.userId()));
        Map<Long, List<String>> materialsTags = materialMapper.selectList(
                new QueryWrapper<MaterialEntity>()
                        .select("id", "tag")
                        .in("id", result)
        ).stream().collect(Collectors.toMap(MaterialEntity::getId,MaterialEntity::getTags));
        Optional<MaterialMatch> bestMaterial=doubleTagsModule(userTags,materialsTags);
        Optional<QueryCandidateResponse> matched = bestMaterial
                .map(MaterialMatch::materialId)
                .flatMap(materialId -> candidates.stream().filter(candidate -> Objects.equals(candidate.materialId(), materialId))
                .findFirst());

        planPriceCachePort.writePlanPriceToRedis(deliveryConverter.queryCandidateTOPlanPrice(matched));

        MaterialMetadata materialMetadata = materialMapper.selectMetadataById(bestMaterial.get().materialId());
        metadataCachePort.writeMetadataToRedis(materialMetadata);
        String deliveryId = matched.get().materialId().toString()+matched.get().planId().toString();

        return deliveryConverter.toResponse(materialMetadata,deliveryId);
    }
    private Optional<MaterialMatch> doubleTagsModule(List<String>  userTags, Map<Long,List<String>> materialsTags){
        return materialsTags.entrySet().stream()
                .map(entry -> new MaterialMatch(
                        entry.getKey(),
                        score(userTags, entry.getValue())))
                .max(Comparator.comparingInt(MaterialMatch::score));
    }

    private int score(List<String> userTags, List<String> value) {
        return  ThreadLocalRandom.current().nextInt(101);//预留接入双塔模型的接口，本项目重心在平台的搭建，故略去算法部分
    }

    private record MaterialMatch(
            Long materialId,
            Integer score
    ){}
}
