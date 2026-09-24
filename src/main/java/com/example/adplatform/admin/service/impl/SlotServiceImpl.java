package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.SlotConverter;
import com.example.adplatform.admin.port.slot.SlotCacheAdminPort;
import com.example.adplatform.admin.port.slot.SlotDebeziumPort;
import com.example.adplatform.admin.port.slot.SlotFilterPort;
import com.example.adplatform.admin.request.CreateSlotRequest;
import com.example.adplatform.admin.request.UpdateSlotRequest;
import com.example.adplatform.admin.request.UpdateSlotStatusRequest;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.service.SlotService;
import com.example.adplatform.admin.response.AvailableSlotResponse;
import com.example.adplatform.admin.response.SlotResponse;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.id.PublicIdGenerator;
import com.example.adplatform.infra.redis.delivery.slot.LockAcquireAttempt;
import com.example.adplatform.infra.redis.delivery.slot.SlotCacheLockManager;
import com.example.adplatform.search.candidate.event.ConfigStopGuardEvent;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;


/**
 * 广告位管理服务，同时维护 Redis 广告位缓存和 ES 配置同步 Outbox。
 *
 * <p>广告位停用会影响该位置下的全部候选，因此状态更新后发布 SLOT 聚合消息，并在事务
 * 提交后写入 Redis 停投保护。</p>
 */
@RequiredArgsConstructor
@Service
public class SlotServiceImpl implements SlotService {

    private final SlotMapper slotMapper;
    private final SlotConverter slotConverter;
    private final SlotFilterPort slotFilterService;
    private final SearchOutboxService searchOutboxService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final SlotCacheAdminPort slotCacheAdminPort;
    private final SlotCacheLockManager lockManager;
    private final SlotDebeziumPort slotDebeziumPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceRefResponse create(CreateSlotRequest request) {
        if (slotMapper.exists(new LambdaQueryWrapper<SlotEntity>().eq(SlotEntity::getSlotCode,request.slotCode()))){
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,"广告位编码已存在");
        }
        SlotEntity slotEntity = slotConverter.toEntity(request);
        slotEntity.initializePublicId(PublicIdGenerator.generate(PublicIdGenerator.SLOT_PREFIX));
        try {
            slotMapper.insert(slotEntity);
        } catch (DuplicateKeyException ex) {
            slotEntity.refreshPublicId(PublicIdGenerator.generate(PublicIdGenerator.SLOT_PREFIX));
            try{
                slotMapper.insert(slotEntity);
            }catch (DuplicateKeyException exception)
            {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "广告位生成PublicId再次重复,请刷新后重试");
            }
        }
        String slotCode = slotEntity.getSlotCode();
        if (Objects.equals(slotEntity.getStatus(), CommonStatus.ENABLED)) {// 布隆过滤器允许假阳性：提交前加入可避免提交后的假阴性误杀。
            slotFilterService.addSlotFilter(slotCode);
            applicationEventPublisher.publishEvent(new SlotCacheCreateEvent(slotCode, slotEntity.getId()));
        }
        slotDebeziumPort.writeRedisWithRetry(slotEntity.getId(),slotCode);
        searchOutboxService.appendConfigChange(ConfigAggregateType.SLOT, slotEntity.getPublicId());
        return slotConverter.toRef(slotEntity);
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlotResponse update(String publicId, UpdateSlotRequest request) {
        SlotEntity oldEntity = slotMapper.selectOne(new LambdaQueryWrapper<SlotEntity>()
                .eq(SlotEntity::getPublicId, publicId));
        if (oldEntity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告位不存在");
        }
        String oldSlotCode = oldEntity.getSlotCode();
        SlotEntity newEntity = slotConverter.updateEntity(request, oldEntity);
        try {
            slotMapper.updateById(newEntity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "广告位编码已存在");
        }
        String newSlotCode = request.slotCode();
        if (Objects.equals(oldEntity.getStatus(), CommonStatus.ENABLED)) {// 布隆过滤器允许假阳性：提交前加入可避免提交后的假阴性误杀。
            slotFilterService.addSlotFilter(newSlotCode);//todo：如何删除？重建机制？
        }
        if (!Objects.equals(oldSlotCode, newSlotCode)) {
            applicationEventPublisher.publishEvent(new SlotCacheUpdateEvent(oldSlotCode,newSlotCode,newEntity.getId()));
            slotDebeziumPort.updateRedisWithRetry(newEntity.getId(),oldSlotCode,newSlotCode);
        }
        searchOutboxService.appendConfigChange(ConfigAggregateType.SLOT, publicId);
        return slotConverter.toResponse(newEntity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlotResponse updateStatus(String publicId, UpdateSlotStatusRequest request) {
        SlotEntity slotEntity = slotMapper.selectOne(new LambdaQueryWrapper<SlotEntity>()
                .eq(SlotEntity::getPublicId, publicId));
        if (slotEntity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告位不存在");
        }
        String slotCode = slotEntity.getSlotCode();
        slotEntity.setStatus(request.status());
        slotMapper.updateById(slotEntity);
        if (Objects.equals(slotEntity.getStatus(), CommonStatus.ENABLED)){
            slotFilterService.addSlotFilter(slotCode);
        }
        applicationEventPublisher.publishEvent(new SlotCacheUpdateStatusEvent(slotCode,slotEntity.getId(),slotEntity.getStatus()));
        slotDebeziumPort.writeRedisWithRetry(slotEntity.getId(),slotCode);
        searchOutboxService.appendConfigChange(ConfigAggregateType.SLOT, publicId);
        applicationEventPublisher.publishEvent(new ConfigStopGuardEvent(
                ConfigAggregateType.SLOT,
                slotEntity.getId(),
                !Objects.equals(request.status(), CommonStatus.ENABLED)));
        return slotConverter.toResponse(slotEntity);
    }

    @Override
    public PageResponse<SlotResponse> pageQuery(long current, long size, String slotCode, Integer status) {
        Page<SlotEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<SlotEntity> query = new LambdaQueryWrapper<SlotEntity>()
                .like(StringUtils.hasText(slotCode), SlotEntity::getSlotCode, slotCode)
                .eq(status != null, SlotEntity::getStatus, status)
                .orderByDesc(SlotEntity::getId);
        Page<SlotEntity> result = slotMapper.selectPage(page, query);
        List<SlotResponse> records = result.getRecords().stream().map(slotConverter::toResponse).toList();
        return PageResponse.of(result, records);
    }
    @Override
    public List<AvailableSlotResponse> listAvailable() {
        return slotMapper.selectList(new LambdaQueryWrapper<SlotEntity>()
                        .eq(SlotEntity::getStatus, CommonStatus.ENABLED)
                        .orderByAsc(SlotEntity::getSlotCode))
                .stream()
                .map(slotConverter::toAvailableResponse)
                .toList();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(SlotCacheCreateEvent event) {
        slotCacheAdminPort.writeSlotToRedis(event.slotId(), event.slotCode());
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(SlotCacheUpdateEvent event) {//todo:锁机制
        try (LockAcquireAttempt ignored = lockManager.acquireForWrite(event.oldSlotCode)) {
            slotCacheAdminPort.evictSlotCodeFromRedis(event.oldSlotCode);
            slotCacheAdminPort.writeSlotToRedis(event.Id,event.newSlotCode);
        }
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(SlotCacheUpdateStatusEvent event){
        if(event.status==CommonStatus.ENABLED){
            slotCacheAdminPort.writeSlotToRedis(event.slotId(), event.slotCode());
        }else slotCacheAdminPort.evictSlotCodeFromRedis(event.slotCode());
    }
    public record SlotCacheUpdateEvent( String oldSlotCode,String newSlotCode,Long Id) {
    }
    public record SlotCacheCreateEvent(String slotCode,long slotId) {
    }
    public record SlotCacheUpdateStatusEvent(String slotCode,long slotId,Integer status){
    }
}
