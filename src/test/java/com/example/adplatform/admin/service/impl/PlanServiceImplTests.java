package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.util.AdvertiserGuardUtils;

import com.example.adplatform.admin.converter.PlanConverter;
import com.example.adplatform.admin.entity.*;
import com.example.adplatform.admin.mapper.*;
import com.example.adplatform.admin.request.plan.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import java.sql.SQLException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlanServiceImplTests {
    private final PlanMapper mapper = mock(PlanMapper.class);
    private final AdvertiserMapper advertisers = mock(AdvertiserMapper.class);

    private final PlanServiceImpl service = new PlanServiceImpl(mapper, new AdvertiserGuardUtils(advertisers), Mappers.getMapper(PlanConverter.class),
            mock(ApplicationEventPublisher.class));

    @Test
    void shouldRejectMissingAdvertiserBeforeCreateOrOnline() {
        assertThatThrownBy(() -> service.create(new CreatePlanRequest(1L, "计划", 100L, 10L, 1L,
                "CPC", LocalDateTime.now(), LocalDateTime.now().plusDays(1))))
                .hasMessage("广告主不存在");
        when(mapper.selectById(any())).thenReturn(plan());
        assertThatThrownBy(() -> service.online(new UpdatePlanStatusRequest(1L)))
                .hasMessage("广告主不存在");
        verify(mapper, never()).insert(any(PlanEntity.class));
        verify(mapper, never()).updateById(any(PlanEntity.class));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0})
    void shouldRejectUnavailableAdvertiserBeforeCreateOrOnline(Integer status) {
        AdvertiserEntity advertiser = new AdvertiserEntity();
        advertiser.setStatus(status);
        when(advertisers.selectById(any())).thenReturn(advertiser);
        assertThatThrownBy(() -> service.create(new CreatePlanRequest(1L, "计划", 100L, 10L, 1L,
                "CPC", LocalDateTime.now(), LocalDateTime.now().plusDays(1))))
                .hasMessage("广告主已停用");
        when(mapper.selectById(any())).thenReturn(plan());
        assertThatThrownBy(() -> service.online(new UpdatePlanStatusRequest(1L)))
                .hasMessage("广告主已停用");
        verify(mapper, never()).insert(any(PlanEntity.class));
        verify(mapper, never()).updateById(any(PlanEntity.class));
    }

    @Test
    void shouldRejectZeroUpdateCountBeforePublishingChanges() {
        when(mapper.selectById(any())).thenReturn(plan());
        when(mapper.updateById(any(PlanEntity.class))).thenReturn(0);
        assertThatThrownBy(() -> service.update(new UpdatePlanRequest(1L, "新名称", null, null, null, null, null, null))).hasMessage("广告计划不存在");
    }

    @Test
    void shouldCreateOfflineWithAdvertiserId() {
        AdvertiserEntity advertiser = new AdvertiserEntity();
        advertiser.setStatus(1);
        when(advertisers.selectById(any())).thenReturn(advertiser);
        service.create(new CreatePlanRequest(1L, "计划", 100L, 10L, 1L, "CPC",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1)));
        ArgumentCaptor<PlanEntity> inserted = ArgumentCaptor.forClass(PlanEntity.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getAdvertiserId()).isEqualTo(1L);
        assertThat(inserted.getValue().getStatus()).isEqualTo("OFFLINE");
        verify(advertisers).selectById(1L);
    }

    @Test
    void shouldUpdateBusinessFieldsWithoutStatusOrOwner() {
        PlanEntity entity = plan();
        when(mapper.selectById(any())).thenReturn(entity);
        when(mapper.selectById(1L)).thenReturn(entity);
        when(mapper.updateById(any(PlanEntity.class))).thenReturn(1);
        service.update(new UpdatePlanRequest(1L, "新计划", 100L, 10L, 1L, "CPC",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1)));
        ArgumentCaptor<PlanEntity> updated = ArgumentCaptor.forClass(PlanEntity.class);
        verify(mapper).updateById(updated.capture());
        assertThat(updated.getValue().getStatus()).isNull();
        assertThat(updated.getValue().getAdvertiserId()).isNull();
        assertThat(entity.getStatus()).isEqualTo("ONLINE");
    }

    @Test
    void shouldDeleteAndRejectRepeatedDeletion() {
        when(mapper.deleteById(any(Long.class))).thenReturn(1, 0);
        assertThat(service.delete(new DeletePlanRequest(1L)).id()).isEqualTo(1L);
        assertThatThrownBy(() -> service.delete(new DeletePlanRequest(1L))).hasMessage("广告计划不存在");
    }

    @Test
    void shouldPreserveOmittedBillingTypeAndValidateMergedBudget() {
        PlanEntity entity = plan();
        entity.setBudgetTotal(100L);
        entity.setBudgetDaily(50L);
        entity.setBillingType("CPM");
        when(mapper.selectById(any())).thenReturn(entity);
        when(mapper.updateById(any(PlanEntity.class))).thenReturn(1);
        service.update(new UpdatePlanRequest(1L, "新名称", null, null, null, null, null, null));
        ArgumentCaptor<PlanEntity> update = ArgumentCaptor.forClass(PlanEntity.class);
        verify(mapper).updateById(update.capture());
        assertThat(update.getValue().getBillingType()).isNull();
        assertThat(update.getValue().getBudgetTotal()).isNull();
        assertThatThrownBy(() -> service.update(new UpdatePlanRequest(1L, null, 40L,
                null, null, null, null, null))).hasMessage("日预算必须小于等于总预算");
    }

    @Test
    void shouldTranslateMissingAdvertiserDuringInsertAndPreserveOtherFailures() {
        AdvertiserEntity advertiser = new AdvertiserEntity();
        advertiser.setStatus(1);
        when(advertisers.selectById(any())).thenReturn(advertiser);
        var request = new CreatePlanRequest(1L, "计划", 100L, 10L, 1L, "CPC",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        var missing = new DataIntegrityViolationException("missing reference",
                new RuntimeException(new SQLException("foreign key conflict", "23000", 1452)));
        when(mapper.insert(any(PlanEntity.class))).thenThrow(missing);
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND))
                .hasMessage("广告主不存在").hasCause(missing);
        var unexpected = new DataIntegrityViolationException("unexpected",
                new SQLException("not null violation", "23000", 1048));
        doThrow(unexpected).when(mapper).insert(any(PlanEntity.class));
        assertThatThrownBy(() -> service.create(request)).isSameAs(unexpected);
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insert(any(PlanEntity.class));
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class).hasMessage("该广告主下已存在同名计划");
    }

    private PlanEntity plan() {
        PlanEntity entity = new PlanEntity();
        entity.setId(1L);
        entity.setId(1L);
        entity.setAdvertiserId(1L);
        entity.setStatus("ONLINE");
        entity.setBudgetTotal(100L);
        entity.setBudgetDaily(10L);
        entity.setStartTime(LocalDateTime.now());
        entity.setEndTime(LocalDateTime.now().plusDays(1));
        return entity;
    }
}
