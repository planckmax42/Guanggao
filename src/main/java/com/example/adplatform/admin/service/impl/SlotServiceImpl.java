package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.SlotConverter;
import com.example.adplatform.admin.dto.CreateSlotRequest;
import com.example.adplatform.admin.dto.UpdateSlotRequest;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.service.SlotService;
import com.example.adplatform.admin.vo.SlotVO;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefVO;
import com.example.adplatform.infra.redis.slot.SlotCacheService;
import com.example.adplatform.search.candidate.event.ConfigStopGuardEvent;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@RequiredArgsConstructor
@Service
public class SlotServiceImpl implements SlotService {

    private final SlotMapper slotMapper;
    private final SlotConverter slotConverter;
    private final SlotCacheService slotCacheService;
    private final SearchOutboxService searchOutboxService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceRefVO create(CreateSlotRequest request) {
        SlotEntity entity = slotConverter.toEntity(request);
        try {
            slotMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "广告位编码已存在");
        }
        slotCacheService.cacheSlot(entity);
        searchOutboxService.appendConfigChange(ConfigAggregateType.SLOT, entity.getId());
        return slotConverter.toRef(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlotVO update(Long id, UpdateSlotRequest request) {
        SlotEntity entity = getSlotOrThrow(id);
        String oldSlotCode = entity.getSlotCode();
        slotConverter.updateEntity(request, entity);
        try {
            slotMapper.updateById(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "广告位编码已存在");
        }

        SlotEntity updated = slotMapper.selectById(id);
        slotCacheService.refreshSlot(updated, oldSlotCode);
        searchOutboxService.appendConfigChange(ConfigAggregateType.SLOT, id);
        applicationEventPublisher.publishEvent(new ConfigStopGuardEvent(
                ConfigAggregateType.SLOT,
                id,
                updated.getStatus() == null || updated.getStatus() != 1));
        return slotConverter.toVO(updated);
    }

    @Override
    public PageResponse<SlotVO> pageQuery(long current, long size, String slotCode, Integer status) {
        Page<SlotEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<SlotEntity> query = new LambdaQueryWrapper<SlotEntity>()
                .like(StringUtils.hasText(slotCode), SlotEntity::getSlotCode, slotCode)
                .eq(status != null, SlotEntity::getStatus, status)
                .orderByDesc(SlotEntity::getId);
        Page<SlotEntity> result = slotMapper.selectPage(page, query);
        List<SlotVO> records = result.getRecords().stream().map(slotConverter::toVO).toList();
        return PageResponse.of(result, records);
    }

    private SlotEntity getSlotOrThrow(Long id) {
        SlotEntity entity = slotMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告位不存在");
        }
        return entity;
    }
}
