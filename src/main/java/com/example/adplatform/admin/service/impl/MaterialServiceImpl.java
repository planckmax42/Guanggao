package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.request.material.UpdateMaterialRequest;
import com.example.adplatform.admin.request.material.DeleteMaterialRequest;
import com.example.adplatform.admin.request.material.PageMaterialRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.MaterialConverter;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.mapper.AdvertiserMapper;
import com.example.adplatform.admin.request.material.CreateMaterialRequest;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.response.material.MaterialOperationResponse;
import com.example.adplatform.admin.service.MaterialService;
import com.example.adplatform.admin.response.material.MaterialQueryResponse;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.common.id.PublicIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 素材管理服务。
 *
 * <p>素材数据与配置 Outbox 在同一 MySQL 事务提交；审核拒绝时再发布事务后本地事件，
 * 立即写入 Redis 停投集合，覆盖 Kafka 尚未同步 ES 的短暂窗口。</p>
 */
@RequiredArgsConstructor
@Service
public class MaterialServiceImpl implements MaterialService {

    private final MaterialMapper materialMapper;
    private final PlanMapper planMapper;
    private final SlotMapper slotMapper;
    private final MaterialConverter materialConverter;
    private final AdvertiserMapper advertiserMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialOperationResponse create(CreateMaterialRequest request) {
        if(!advertiserMapper.exists(new LambdaQueryWrapper<AdvertiserEntity>()
                .eq(AdvertiserEntity::getPublicId, request.advertiserPublicId())))
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"广告主不存在");
        if(!slotMapper.exists(new LambdaQueryWrapper<SlotEntity>()
                .eq(SlotEntity::getPublicId, request.slotPublicId())))
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"广告位不存在");
        MaterialEntity entity = materialConverter.createRequestToEntity(request);
        entity.initializePublicId(PublicIdGenerator.generate(PublicIdGenerator.MATERIAL_PREFIX));
        entity.setAdvertiserPublicId(request.advertiserPublicId());
        entity.setSlotPublicId(request.slotPublicId());
        materialMapper.insert(entity);
        return materialConverter.entityToOperationResponse(entity);
    }

    @Override
    public PageResponse<MaterialQueryResponse> pageQuery(PageMaterialRequest request) {
        Page<MaterialEntity> page = new Page<>(request.current(), request.size());
        Page<MaterialEntity> result = materialMapper.selectPage(page, new LambdaQueryWrapper<MaterialEntity>()
                .orderByAsc(MaterialEntity::getName)
                .orderByAsc(MaterialEntity::getId));
        return materialConverter.pageToResponse(result);
    }

    @Override
    public MaterialOperationResponse update(UpdateMaterialRequest request) {
        if (request.slotPublicId() != null && !slotMapper.exists(new LambdaQueryWrapper<SlotEntity>()
                .eq(SlotEntity::getPublicId, request.slotPublicId()))) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告位不存在");
        }
        MaterialEntity entity = materialConverter.updateRequestToEntity(request);
        int updated = materialMapper.update(entity, new LambdaUpdateWrapper<MaterialEntity>()
                .eq(MaterialEntity::getPublicId, request.publicId()));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "素材不存在");
        }
        return new MaterialOperationResponse(request.publicId());
    }

    @Override
    public MaterialOperationResponse delete(DeleteMaterialRequest request) {
        int deleted = materialMapper.delete(new LambdaQueryWrapper<MaterialEntity>()
                .eq(MaterialEntity::getPublicId, request.publicId()));
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "素材不存在");
        }
        return new MaterialOperationResponse(request.publicId());
    }
}
