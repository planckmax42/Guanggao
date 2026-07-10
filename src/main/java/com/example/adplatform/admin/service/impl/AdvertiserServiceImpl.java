package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.AdvertiserConverter;
import com.example.adplatform.admin.dto.CreateAdvertiserRequest;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.mapper.AdvertiserMapper;
import com.example.adplatform.admin.service.AdvertiserService;
import com.example.adplatform.admin.vo.AdvertiserVO;
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
    private final AdvertiserConverter advertiserConverter;

    @Override
    public ResourceRefVO create(CreateAdvertiserRequest request) {
        AdvertiserEntity entity = advertiserConverter.toEntity(request);
        try {
            advertiserMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "广告主名称已存在");
        }
        return advertiserConverter.toRef(entity);
    }

    @Override
    public PageResponse<AdvertiserVO> pageQuery(long current, long size, String name, Integer status) {
        Page<AdvertiserEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<AdvertiserEntity> query = new LambdaQueryWrapper<AdvertiserEntity>()
                .like(StringUtils.hasText(name), AdvertiserEntity::getName, name)
                .eq(status != null, AdvertiserEntity::getStatus, status)
                .orderByDesc(AdvertiserEntity::getId);
        Page<AdvertiserEntity> result = advertiserMapper.selectPage(page, query);
        List<AdvertiserVO> records = result.getRecords().stream().map(advertiserConverter::toVO).toList();
        return PageResponse.of(result, records);
    }
}
