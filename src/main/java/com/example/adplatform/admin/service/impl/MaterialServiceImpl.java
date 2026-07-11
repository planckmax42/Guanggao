package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.MaterialConverter;
import com.example.adplatform.admin.dto.AuditMaterialRequest;
import com.example.adplatform.admin.dto.CreateMaterialRequest;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.service.MaterialService;
import com.example.adplatform.admin.vo.MaterialVO;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@RequiredArgsConstructor
@Service
public class MaterialServiceImpl implements MaterialService {

    private final MaterialMapper materialMapper;
    private final PlanMapper planMapper;
    private final SlotMapper slotMapper;
    private final MaterialConverter materialConverter;

    @Override
    public ResourceRefVO create(CreateMaterialRequest request) {
        PlanEntity plan = planMapper.selectById(request.planId());
        if (plan == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
        }
        SlotEntity slot = slotMapper.selectById(request.slotId());
        if (slot == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告位不存在");
        }

        MaterialEntity entity = materialConverter.toEntity(request);
        materialMapper.insert(entity);
        return materialConverter.toRef(entity);
    }

    @Override
    public MaterialVO audit(Long id, AuditMaterialRequest request) {
        MaterialEntity entity = materialMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告素材不存在");
        }
        entity.setAuditStatus(request.auditStatus());
        materialMapper.updateById(entity);
        return materialConverter.toVO(materialMapper.selectById(id));
    }

    @Override
    public PageResponse<MaterialVO> pageQuery(long current, long size, Long planId, String auditStatus) {
        Page<MaterialEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<MaterialEntity> query = new LambdaQueryWrapper<MaterialEntity>()
                .eq(planId != null, MaterialEntity::getPlanId, planId)
                .eq(StringUtils.hasText(auditStatus), MaterialEntity::getAuditStatus, auditStatus)
                .orderByDesc(MaterialEntity::getId);
        Page<MaterialEntity> result = materialMapper.selectPage(page, query);
        List<MaterialVO> records = result.getRecords().stream().map(materialConverter::toVO).toList();
        return PageResponse.of(result, records);
    }
}
