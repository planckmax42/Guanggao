package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.SlotConverter;
import com.example.adplatform.admin.dto.CreateSlotRequest;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.service.SlotService;
import com.example.adplatform.admin.vo.SlotVO;
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
public class SlotServiceImpl implements SlotService {

    private final SlotMapper slotMapper;
    private final SlotConverter slotConverter;

    @Override
    public ResourceRefVO create(CreateSlotRequest request) {
        SlotEntity entity = slotConverter.toEntity(request);
        try {
            slotMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "广告位编码已存在");
        }
        return slotConverter.toRef(entity);
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
}
