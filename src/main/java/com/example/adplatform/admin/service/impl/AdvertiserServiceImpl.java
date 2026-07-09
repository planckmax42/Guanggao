package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.dto.CreateAdvertiserRequest;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.mapper.AdvertiserMapper;
import com.example.adplatform.admin.service.AdvertiserService;
import com.example.adplatform.admin.vo.AdvertiserVO;
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
public class AdvertiserServiceImpl implements AdvertiserService {

    private final AdvertiserMapper advertiserMapper;

    @Override
    public ResourceRefVO create(CreateAdvertiserRequest request) {
        AdvertiserEntity entity = new AdvertiserEntity();
        entity.setName(request.name());
        entity.setIndustry(request.industry());
        entity.setContactName(request.contactName());
        entity.setContactEmail(request.contactEmail());
        entity.setStatus(CommonStatus.ENABLED);
        try {
            advertiserMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "广告主名称已存在");
        }
        return new ResourceRefVO(entity.getId(), entity.getName());
    }

    @Override
    public PageResponse<AdvertiserVO> pageQuery(long current, long size, String name, Integer status) {
        Page<AdvertiserEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<AdvertiserEntity> query = new LambdaQueryWrapper<AdvertiserEntity>()
                .like(StringUtils.hasText(name), AdvertiserEntity::getName, name)
                .eq(status != null, AdvertiserEntity::getStatus, status)
                .orderByDesc(AdvertiserEntity::getId);
        Page<AdvertiserEntity> result = advertiserMapper.selectPage(page, query);
        List<AdvertiserVO> records = result.getRecords().stream().map(this::toVO).toList();
        return PageResponse.of(result, records);
    }

    private AdvertiserVO toVO(AdvertiserEntity entity) {
        return new AdvertiserVO(
                entity.getId(),
                entity.getName(),
                entity.getIndustry(),
                entity.getContactName(),
                entity.getContactEmail(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
