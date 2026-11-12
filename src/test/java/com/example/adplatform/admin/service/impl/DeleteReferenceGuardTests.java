package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.util.AdvertiserGuardUtils;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;

import com.example.adplatform.admin.converter.*;
import com.example.adplatform.admin.mapper.*;
import com.example.adplatform.admin.request.advertiser.DeleteAdvertiserRequest;
import com.example.adplatform.admin.request.material.DeleteMaterialRequest;
import com.example.adplatform.admin.request.plan.DeletePlanRequest;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeleteReferenceGuardTests {

    @Test
    void protectsAdvertiserReferencesAndPreservesDeleteResults() {
        AdvertiserMapper mapper = mock(AdvertiserMapper.class);
        var service = new AdvertiserServiceImpl(mapper, mock(AdvertiserConverter.class));
        var request = new DeleteAdvertiserRequest("public-id");
        var failure = new DataIntegrityViolationException("referenced",
                new SQLException("foreign key conflict", "23000", 1451));
        when(mapper.delete(any(Wrapper.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.delete(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_IN_USE))
                .hasMessage("广告主存在关联数据，不能删除")
                .hasCause(failure);

        doReturn(1, 0).when(mapper).delete(any(Wrapper.class));
        assertThat(service.delete(request).publicId()).isEqualTo("public-id");
        assertThatThrownBy(() -> service.delete(request)).hasMessage("广告主不存在");
        verify(mapper, times(3)).delete(any(Wrapper.class));
        verifyNoMoreInteractions(mapper);
        var unexpected = new DataIntegrityViolationException("unexpected",
                new SQLException("not null violation", "23000", 1048));
        doThrow(unexpected).when(mapper).delete(any(Wrapper.class));
        assertThatThrownBy(() -> service.delete(request)).isSameAs(unexpected);
    }

    @Test
    void protectsMaterialReferencesAndPreservesDeleteResults() {
        MaterialMapper mapper = mock(MaterialMapper.class);
        var service = new MaterialServiceImpl(mapper, mock(MaterialConverter.class), mock(AdvertiserGuardUtils.class));
        var request = new DeleteMaterialRequest("public-id");
        var failure = new DataIntegrityViolationException("referenced",
                new SQLException("foreign key conflict", "23000", 1451));
        when(mapper.delete(any(Wrapper.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.delete(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_IN_USE))
                .hasMessage("素材存在关联数据，不能删除")
                .hasCause(failure);

        doReturn(1, 0).when(mapper).delete(any(Wrapper.class));
        assertThat(service.delete(request).publicId()).isEqualTo("public-id");
        assertThatThrownBy(() -> service.delete(request)).hasMessage("素材不存在");
        verify(mapper, times(3)).delete(any(Wrapper.class));
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void protectsPlanReferencesAndPreservesDeleteResults() {
        PlanMapper mapper = mock(PlanMapper.class);
        var service = new PlanServiceImpl(mapper, mock(AdvertiserGuardUtils.class), mock(PlanConverter.class), mock(SearchOutboxService.class), mock(ApplicationEventPublisher.class));
        var request = new DeletePlanRequest("public-id");
        var failure = new DataIntegrityViolationException("referenced",
                new SQLException("foreign key conflict", "23000", 1451));
        when(mapper.delete(any(Wrapper.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.delete(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_IN_USE))
                .hasMessage("广告计划存在关联数据，不能删除")
                .hasCause(failure);

        doReturn(1, 0).when(mapper).delete(any(Wrapper.class));
        assertThat(service.delete(request).publicId()).isEqualTo("public-id");
        assertThatThrownBy(() -> service.delete(request)).hasMessage("广告计划不存在");
        verify(mapper, times(3)).delete(any(Wrapper.class));
        verifyNoMoreInteractions(mapper);
    }
}
