package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.request.material.UpdateMaterialRequest;
import com.example.adplatform.admin.request.material.DeleteMaterialRequest;
import com.example.adplatform.admin.request.material.PageMaterialRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.MaterialConverter;
import com.example.adplatform.admin.util.AdvertiserGuardUtils;
import com.example.adplatform.admin.request.material.CreateMaterialRequest;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.enums.MaterialAuditStatus;
import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.response.material.MaterialOperationResponse;
import com.example.adplatform.admin.service.MaterialService;
import com.example.adplatform.admin.response.material.MaterialQueryResponse;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.util.MySqlForeignKeyUtils;
import com.example.adplatform.admin.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final MaterialConverter materialConverter;
    private final AdvertiserGuardUtils advertiserGuardUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialOperationResponse create(CreateMaterialRequest request) {
        advertiserGuardUtils.requireEnabled(request.advertiserId());
        MaterialEntity entity = materialConverter.createRequestToEntity(request);

        try {
            materialMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "该广告主下已存在同名素材");
        } catch (DataIntegrityViolationException ex) {
            if (MySqlForeignKeyUtils.isReferencedRowMissing(ex)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "关联的广告主或广告位不存在", ex);
            }
            throw ex;
        }
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
        MaterialEntity existing = materialMapper.selectById(request.id());
        if (existing == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "素材不存在");
        }
        boolean changed = (request.slotId() != null && !request.slotId().equals(existing.getSlotId()))
                || (request.name() != null && !request.name().equals(existing.getName()))
                || (request.description() != null && !request.description().equals(existing.getDescription()))
                || (request.imageUrl() != null && !request.imageUrl().equals(existing.getImageUrl()))
                || (request.landingPageUrl() != null && !request.landingPageUrl().equals(existing.getLandingPageUrl()));
        if (!changed) {
            return new MaterialOperationResponse(request.id());
        }
        MaterialEntity entity = materialConverter.updateRequestToEntity(request);
        entity.setId(existing.getId());
        entity.setAuditStatus(MaterialAuditStatus.PENDING.name());
        try {
            if (materialMapper.updateById(entity) == 0) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "素材不存在");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "该广告主下已存在同名素材");
        } catch (DataIntegrityViolationException ex) {
            if (MySqlForeignKeyUtils.isReferencedRowMissing(ex)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告位不存在", ex);
            }
            throw ex;
        }
        return new MaterialOperationResponse(request.id());
    }

    @Override
    public MaterialOperationResponse delete(DeleteMaterialRequest request) {
        try {
            if (materialMapper.deleteById(request.id()) == 0) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "素材不存在");
            }
        } catch (DataIntegrityViolationException ex) {
            if (MySqlForeignKeyUtils.isReferencedByOtherRows(ex)) {
                throw new BusinessException(ErrorCode.RESOURCE_IN_USE, "素材存在关联数据，不能删除", ex);
            }
            throw ex;
        }
        return new MaterialOperationResponse(request.id());
    }
}
