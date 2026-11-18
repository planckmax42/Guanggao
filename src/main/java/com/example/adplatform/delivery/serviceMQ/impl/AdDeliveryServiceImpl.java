package com.example.adplatform.delivery.serviceMQ.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.delivery.converter.DeliveryConverter;
import com.example.adplatform.delivery.portMQ.DeliveryElasticSearchPort;
import com.example.adplatform.delivery.portMQ.DeliverySlotCachePort;
import com.example.adplatform.delivery.portMQ.DeliveryStopGuardPort;
import com.example.adplatform.delivery.request.DeliveryRequest;
import com.example.adplatform.delivery.response.AdDeliveryResponse;
import com.example.adplatform.infra.bloomfilter.delivery.slot.SlotBloomOperationsService;
import com.example.adplatform.infra.elasticsearch.QueryCandidateRequest;
import com.example.adplatform.infra.elasticsearch.QueryCandidateResponse;
import com.example.adplatform.infra.redis.delivery.slot.MQ.RedisOperationResult;
import com.example.adplatform.infra.redis.delivery.slot.MQ.RedisOperationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdDeliveryServiceImpl {

    private final IdentifierGenerator identifierGenerator;
    private final DeliverySlotCachePort slotCachePort;
    private final DeliveryElasticSearchPort elasticSearchPort;
    private final SlotBloomOperationsService slotBloomOperationsService;
    private final DeliveryConverter deliveryConverter;
    private final DeliveryStopGuardPort deliveryStopGuardPort;
    private final SlotMapper slotMapper;

    public AdDeliveryResponse delivery (DeliveryRequest request){
        if(deliveryStopGuardPort.isSlotStopped(request.slotCode()))
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
        QueryCandidateRequest candidateRequest = deliveryConverter.deliveryToCandidateRequest(request,slotId);
        List<QueryCandidateResponse> candidates= elasticSearchPort.query(candidateRequest);

    }
}
