package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.dto.CreateAdSlotRequest;
import com.example.adplatform.admin.entity.AdSlotEntity;
import com.example.adplatform.admin.mapper.AdSlotMapper;
import com.example.adplatform.admin.service.AdSlotService;
import com.example.adplatform.admin.vo.AdSlotVO;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@RequiredArgsConstructor
@Service
public class AdSlotServiceImpl implements AdSlotService {

    private final AdSlotMapper adSlotMapper;

    @Override
    public ResourceRefVO create(CreateAdSlotRequest request) {
        AdSlotEntity entity = new AdSlotEntity();
        entity.setSlotCode(request.slotCode());
        entity.setName(request.name());
        entity.setWidth(request.width());
        entity.setHeight(request.height());
        entity.setScene(request.scene());
        entity.setStatus(CommonStatus.ENABLED);
        try {
            adSlotMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "广告位编码已存在");
        }
        return new ResourceRefVO(entity.getId(), entity.getSlotCode());
    }

    @Override
    public PageResponse<AdSlotVO> pageQuery(long current, long size, String slotCode, Integer status) {
        Page<AdSlotEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<AdSlotEntity> query = new LambdaQueryWrapper<AdSlotEntity>()
                .like(StringUtils.hasText(slotCode), AdSlotEntity::getSlotCode, slotCode)
                .eq(status != null, AdSlotEntity::getStatus, status)
                .orderByDesc(AdSlotEntity::getId);
        Page<AdSlotEntity> result = adSlotMapper.selectPage(page, query);
        List<AdSlotVO> records = result.getRecords().stream().map(this::toVO).toList();
        return PageResponse.of(result, records);
    }

    private AdSlotVO toVO(AdSlotEntity entity) {
        return new AdSlotVO(
                entity.getId(),
                entity.getSlotCode(),
                entity.getName(),
                entity.getWidth(),
                entity.getHeight(),
                entity.getScene(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
