package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.request.slot.PageSlotRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.SlotConverter;
import com.example.adplatform.admin.port.slot.*;
import com.example.adplatform.admin.request.slot.CreateSlotRequest;
import com.example.adplatform.admin.request.slot.DeleteSlotRequest;
import com.example.adplatform.admin.request.slot.UpdateSlotRequest;
import com.example.adplatform.admin.request.slot.UpdateSlotStatusRequest;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.response.slot.SlotOperationResponse;
import com.example.adplatform.admin.response.slot.SlotQueryResponse;
import com.example.adplatform.admin.service.SlotService;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.util.MySqlForeignKeyUtils;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.common.enums.CommonStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;


/**
 * 广告位管理服务。基础 CRUD 的缓存、布隆过滤器和 ES 同步暂时停用，状态接口保持原行为。
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
    private final ApplicationEventPublisher applicationEventPublisher;
    private final SlotCacheDebeziumPort slotCacheDebeziumPort;
    private final SlotCacheStopGuardPort slotCacheStopGuardPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlotOperationResponse create(CreateSlotRequest request) {
        SlotEntity slotEntity = slotConverter.createRequestToEntity(request);

        try {
            slotMapper.insert(slotEntity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "广告位编码已存在");//统一改成name？
        }
        // TODO：后续恢复创建后的同步。
        // if (Objects.equals(slotEntity.getStatus(), CommonStatus.ENABLED)) {
        //     String slotCode = slotEntity.getSlotCode();// 布隆过滤器允许假阳性：提交前加入可避免提交后的假阴性误杀。
        //     slotFilterService.addSlotFilter(slotCode);
        //     slotCacheDebeziumPort.writeCacheWithRetry(slotEntity.getId(),slotCode);
        // }
        return slotConverter.entityToOperationResponse(slotEntity);
    }

    @Override
    public PageResponse<SlotQueryResponse> pageQuery(PageSlotRequest request) {
        Page<SlotEntity> page = new Page<>(request.current(), request.size());
        LambdaQueryWrapper<SlotEntity> query = new LambdaQueryWrapper<SlotEntity>()
                .orderByDesc(SlotEntity::getId);
        Page<SlotEntity> result = slotMapper.selectPage(page, query);
        return slotConverter.pageToResponse(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlotOperationResponse update(UpdateSlotRequest request) {
        SlotEntity oldEntity = slotMapper.selectById(request.id());
        if (oldEntity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告位不存在");
        }
        boolean changed = (request.slotCode() != null && !request.slotCode().equals(oldEntity.getSlotCode()))
                || (request.name() != null && !request.name().equals(oldEntity.getName()))
                || (request.width() != null && !request.width().equals(oldEntity.getWidth()))
                || (request.height() != null && !request.height().equals(oldEntity.getHeight()))
                || (request.scene() != null && !request.scene().equals(oldEntity.getScene()));
        if (!changed) {
            return new SlotOperationResponse(request.id());
        }
        SlotEntity entity = slotConverter.updateRequestToEntity(request);
        entity.setId(oldEntity.getId());
        try {
            if (slotMapper.updateById(entity) == 0) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告位不存在");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "广告位编码已存在");//统一改成name？
        }
        // TODO：后续恢复改名后的同步。
        // String oldSlotCode = oldEntity.getSlotCode();
        // String newSlotCode = request.slotCode() == null ? oldSlotCode : request.slotCode();
        // if (!Objects.equals(oldSlotCode, newSlotCode) && Objects.equals(oldEntity.getStatus(), CommonStatus.ENABLED)) {
        //     slotFilterService.addSlotFilter(newSlotCode);
        //     slotCacheDebeziumPort.updateCacheWithRetry(oldEntity.getId(), oldSlotCode, newSlotCode);
        //     slotElasticsearchDebeziumPort.syncElasticsearchWithRetry(oldSlotCode, newSlotCode);
        // }
        return new SlotOperationResponse(request.id());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlotOperationResponse delete(DeleteSlotRequest request) {
        try {
            if (slotMapper.deleteById(request.id()) == 0) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告位不存在");
            }
        } catch (DataIntegrityViolationException ex) {
            if (MySqlForeignKeyUtils.isReferencedByOtherRows(ex)) {
                throw new BusinessException(ErrorCode.RESOURCE_IN_USE, "广告位存在关联数据，不能删除", ex);
            }
            throw ex;
        }
        // TODO：恢复删除同步时，需先查询并保留原 slotCode。
        // slotCacheDebeziumPort.evictCacheWithRetry(entity.getSlotCode());
        return new SlotOperationResponse(request.id());
    }

//    //todo“需要处理缓存不一致问题状态请求查询到 slotCode=A
//    另一个请求把编码修改成 B
//    状态请求只更新 status，数据库字段不会被覆盖
//    状态请求随后却按照旧编码 A 更新 Redis
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlotOperationResponse updateStatus(UpdateSlotStatusRequest request) {
        SlotEntity slotEntity = slotMapper.selectById(request.id());
        if (slotEntity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告位不存在");
        }
        Integer newStatus = request.status();
        String slotCode = slotEntity.getSlotCode();
        Long slotId = slotEntity.getId();
        slotEntity.setStatus(newStatus);
        slotMapper.update(null,new LambdaUpdateWrapper<SlotEntity>()
                .eq(SlotEntity::getId,request.id())
                .set(SlotEntity::getStatus,newStatus));
        if (Objects.equals(newStatus, CommonStatus.ENABLED)){
            applicationEventPublisher.publishEvent(new SlotCacheStopGuardEvictEvent(slotCode));
            slotFilterService.addSlotFilter(slotCode);
            slotCacheDebeziumPort.writeCacheWithRetry(slotId,slotCode);
        }
        if (Objects.equals(newStatus, CommonStatus.DISABLED)){
            applicationEventPublisher.publishEvent(new SlotCacheStopGuardWriteEvent(slotCode));
            slotCacheDebeziumPort.evictCacheWithRetry(slotCode);
        }
        return slotConverter.entityToOperationResponse(slotEntity);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(SlotCacheStopGuardWriteEvent event){//需要重试机制吗
        slotCacheStopGuardPort.writeToStopGuardCache(event.slotCode);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(SlotCacheStopGuardEvictEvent event){
        slotCacheStopGuardPort.evictFromStopGuardCache(event.slotCode);
    }
    public record SlotCacheStopGuardWriteEvent(String slotCode){
    }
    public record SlotCacheStopGuardEvictEvent(String slotCode){
    }
}
