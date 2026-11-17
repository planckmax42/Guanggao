package com.example.adplatform.delivery.serviceMQ.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.delivery.port.SlotIdResult;
import com.example.adplatform.delivery.portMQ.DeliverySlotCachePort;
import com.example.adplatform.delivery.request.AdDeliveryRequest;
import com.example.adplatform.delivery.response.AdDeliveryResponse;
import com.example.adplatform.infra.bloomfilter.delivery.slot.SlotBloomOperationsService;
import com.example.adplatform.infra.redis.delivery.slot.MQ.RedisOperationResult;
import com.example.adplatform.infra.redis.delivery.slot.MQ.RedisOperationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdDeliveryServiceImpl {

    private final IdentifierGenerator identifierGenerator;
    private final DeliverySlotCachePort slotCachePort;
    private final SlotBloomOperationsService slotBloomOperationsService;
    private final SlotMapper slotMapper;

    public AdDeliveryResponse delivery (AdDeliveryRequest request){
        Long slotId;
        if (slotBloomOperationsService.definiteNotContain(request.slotCode()))
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"广告位不存在");
        RedisOperationResult readResult = slotCachePort.getSlotIdFromRedis(request.slotCode());
        RedisOperationStatus status = readResult.status();
        if (status==RedisOperationStatus.CACHE_UNFOUND){
            //熔断器可以配置在全局吗
            SlotEntity entity = slotMapper.selectOne(new LambdaQueryWrapper<SlotEntity>()
                    .eq(SlotEntity::getSlotCode, request.slotCode())
                    .eq(SlotEntity::getStatus, CommonStatus.ENABLED));
            if(entity==null){
                //记录误报
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"广告位不存在");
            }
            RedisOperationResult writeResult = slotCachePort.writeSlotIdFromRedis(request.slotCode(),entity.getId());
            slotId=entity.getId();
        }
        slotId = readResult.slotId();
    }
}
