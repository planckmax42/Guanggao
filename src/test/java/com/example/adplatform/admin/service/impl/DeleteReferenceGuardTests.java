package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.converter.*;
import com.example.adplatform.admin.mapper.*;
import com.example.adplatform.admin.request.advertiser.DeleteAdvertiserRequest;
import com.example.adplatform.admin.request.material.DeleteMaterialRequest;
import com.example.adplatform.admin.request.plan.DeletePlanRequest;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
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
        when(mapper.hasReferences("public-id")).thenReturn(true);
        assertThatThrownBy(() -> service.delete(request))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_IN_USE))
                .hasMessage("广告主存在关联数据，不能删除");
        verify(mapper, never()).delete(any(Wrapper.class));

        when(mapper.hasReferences("public-id")).thenReturn(false);
        when(mapper.delete(any(Wrapper.class))).thenReturn(1, 0);
        assertThat(service.delete(request).publicId()).isEqualTo("public-id");
        assertThatThrownBy(() -> service.delete(request)).hasMessage("广告主不存在");
        verify(mapper, never()).selectOne(any());
    }

    @Test
    void protectsMaterialReferencesAndPreservesDeleteResults() {
        MaterialMapper mapper = mock(MaterialMapper.class);
        var service = new MaterialServiceImpl(mapper, mock(SlotMapper.class), mock(MaterialConverter.class), mock(AdvertiserMapper.class));
        var request = new DeleteMaterialRequest("public-id");
        when(mapper.hasReferences("public-id")).thenReturn(true);
        assertThatThrownBy(() -> service.delete(request))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_IN_USE))
                .hasMessage("素材存在关联数据，不能删除");
        verify(mapper, never()).delete(any(Wrapper.class));

        when(mapper.hasReferences("public-id")).thenReturn(false);
        when(mapper.delete(any(Wrapper.class))).thenReturn(1, 0);
        assertThat(service.delete(request).publicId()).isEqualTo("public-id");
        assertThatThrownBy(() -> service.delete(request)).hasMessage("素材不存在");
        verify(mapper, never()).selectOne(any());
    }

    @Test
    void protectsPlanReferencesAndPreservesDeleteResults() {
        PlanMapper mapper = mock(PlanMapper.class);
        var service = new PlanServiceImpl(mapper, mock(AdvertiserMapper.class), mock(PlanConverter.class), mock(SearchOutboxService.class), mock(ApplicationEventPublisher.class));
        var request = new DeletePlanRequest("public-id");
        when(mapper.hasReferences("public-id")).thenReturn(true);
        assertThatThrownBy(() -> service.delete(request))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_IN_USE))
                .hasMessage("广告计划存在关联数据，不能删除");
        verify(mapper, never()).delete(any(Wrapper.class));

        when(mapper.hasReferences("public-id")).thenReturn(false);
        when(mapper.delete(any(Wrapper.class))).thenReturn(1, 0);
        assertThat(service.delete(request).publicId()).isEqualTo("public-id");
        assertThatThrownBy(() -> service.delete(request)).hasMessage("广告计划不存在");
        verify(mapper, never()).selectOne(any());
    }
}
