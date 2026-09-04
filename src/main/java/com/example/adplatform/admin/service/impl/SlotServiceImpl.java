package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.SlotConverter;
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
import com.example.adplatform.admin.event.SlotCacheImmediateEvent;
import com.example.adplatform.search.candidate.event.ConfigStopGuardEvent;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import com.example.adplatform.search.outbox.service.SlotCacheOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
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
    private final SlotCacheOutboxService slotCacheOutboxService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceRefResponse create(CreateSlotRequest request) {
        SlotEntity slotEntity = slotConverter.toEntity(request);
        String slotCode = slotEntity.getSlotCode();
        slotEntity.initializePublicId(PublicIdGenerator.generate(PublicIdGenerator.SLOT_PREFIX));
        try {
            slotMapper.insert(slotEntity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "广告位编码已存在");
        }
        if (Objects.equals(slotEntity.getStatus(), CommonStatus.ENABLED)) {// 布隆过滤器允许假阳性：提交前加入可避免提交后的假阴性误杀。
            slotFilterService.addSlotFilter(slotCode);
            applicationEventPublisher.publishEvent(new SlotCacheImmediateEvent(
                    SlotCacheImmediateEvent.Action.WRITE, slotCode, slotEntity.getId()));
        }
        slotCacheOutboxService.append(slotEntity.getPublicId(), null);
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
        Long internalId = oldEntity.getId();
        String oldSlotCode = oldEntity.getSlotCode();
        slotConverter.updateEntity(request, oldEntity);
        try {
            slotMapper.updateById(oldEntity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "广告位编码已存在");
        }
        SlotEntity newEntity = slotMapper.selectById(internalId);
        String newSlotCode = newEntity.getSlotCode();
        boolean slotCodeChanged = !Objects.equals(oldSlotCode, newSlotCode);
        if (Objects.equals(newEntity.getStatus(), CommonStatus.ENABLED)) {// 布隆过滤器允许假阳性：提交前加入可避免提交后的假阴性误杀。
            slotFilterService.addSlotFilter(newSlotCode);
        }
        if (slotCodeChanged) {
            applicationEventPublisher.publishEvent(new SlotCacheImmediateEvent(
                    SlotCacheImmediateEvent.Action.EVICT, oldSlotCode, internalId));
            slotCacheOutboxService.append(publicId, oldSlotCode);
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
        applicationEventPublisher.publishEvent(new SlotCacheImmediateEvent(
                Objects.equals(slotEntity.getStatus(), CommonStatus.ENABLED)
                        ? SlotCacheImmediateEvent.Action.WRITE
                        : SlotCacheImmediateEvent.Action.EVICT,
                slotCode,
                slotEntity.getId()));
        slotCacheOutboxService.append(publicId, null);
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
}
