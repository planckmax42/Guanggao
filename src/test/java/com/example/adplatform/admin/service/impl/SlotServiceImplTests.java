package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.SlotConverter;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.port.slot.*;
import com.example.adplatform.admin.request.slot.*;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SlotServiceImplTests {
    private final SlotMapper mapper = mock(SlotMapper.class);
    private final SlotFilterPort filter = mock(SlotFilterPort.class);
    private final SlotCacheDebeziumPort cache = mock(SlotCacheDebeziumPort.class);
    private final SlotElasticsearchDebeziumPort elasticsearch = mock(SlotElasticsearchDebeziumPort.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final SlotCacheStopGuardPort stopGuard = mock(SlotCacheStopGuardPort.class);
    private final SlotServiceImpl service = new SlotServiceImpl(mapper, Mappers.getMapper(SlotConverter.class), filter,
            events, cache, stopGuard);

    @AfterEach
    void basicCrudMustNotSynchronizeExternally() {
        verifyNoInteractions(filter, cache, elasticsearch, events, stopGuard);
    }

    @Test
    void createsSlotWithRequestedFieldsAndStatus() {
        when(mapper.insert(any(SlotEntity.class))).thenAnswer(call -> {
            ((SlotEntity) call.getArgument(0)).setId(1L);
            return 1;
        });
        var response = service.create(new CreateSlotRequest("HOME", "首页", 100, 200, "APP", 1));
        assertThat(response.id()).isEqualTo(1L);
        var inserted = ArgumentCaptor.forClass(SlotEntity.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getSlotCode()).isEqualTo("HOME");
        assertThat(inserted.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateSlotCodesOnCreateAndUpdate() {
        when(mapper.insert(any(SlotEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> service.create(new CreateSlotRequest("HOME", "首页", 100, 200, "APP", 1)))
                .hasMessage("广告位编码已存在");
        when(mapper.selectById(any())).thenReturn(slot());
        when(mapper.updateById(any(SlotEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> service.update(new UpdateSlotRequest(1L, "NEW", null, null, null, null)))
                .hasMessage("广告位编码已存在");
    }

    @Test
    void rejectsMissingRecordsAndZeroUpdateCount() {
        var request = new UpdateSlotRequest(1L, "NEW", null, null, null, null);
        assertThatThrownBy(() -> service.update(request)).hasMessage("广告位不存在");
        when(mapper.selectById(any())).thenReturn(slot());
        when(mapper.updateById(any(SlotEntity.class))).thenReturn(0);
        assertThatThrownBy(() -> service.update(request)).hasMessage("广告位不存在");
    }

    @Test
    void updatesOnlySuppliedFieldsByInternalId() {
        SlotEntity existing = slot();
        when(mapper.selectById(any())).thenReturn(existing);
        when(mapper.updateById(any(SlotEntity.class))).thenReturn(1);
        assertThat(service.update(new UpdateSlotRequest(1L, "NEW", "首页", null, null, null)).id())
                .isEqualTo(1L);
        var update = ArgumentCaptor.forClass(SlotEntity.class);
        verify(mapper).updateById(update.capture());
        assertThat(update.getValue().getId()).isEqualTo(1L);
        assertThat(update.getValue().getSlotCode()).isEqualTo("NEW");
        assertThat(update.getValue().getName()).isEqualTo("首页");
        assertThat(update.getValue().getStatus()).isNull();
        assertThat(update.getValue().getWidth()).isNull();
        assertThat(existing.getSlotCode()).isEqualTo("OLD");
    }

    @Test
    void repeatedUnchangedRequestsDoNotWrite() {
        when(mapper.selectById(any())).thenReturn(slot());
        var request = new UpdateSlotRequest(1L, "OLD", "首页", 100, 200, "APP");
        assertThat(service.update(request).id()).isEqualTo(1L);
        service.update(request);
        verify(mapper, never()).updateById(any(SlotEntity.class));
    }

    @Test
    void deletesWithoutPrequeryAndReportsMissingOnRepeat() {
        when(mapper.deleteById(any(Long.class))).thenReturn(1, 0);
        var request = new DeleteSlotRequest(1L);
        assertThat(service.delete(request).id()).isEqualTo(1L);
        assertThatThrownBy(() -> service.delete(request)).hasMessage("广告位不存在");
        verify(mapper, times(2)).deleteById(any(Long.class));
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void translatesForeignKeyDeleteFailureAndRethrowsOtherErrors() {
        var failure = new DataIntegrityViolationException("referenced", new SQLException("constraint", "23000", 1451));
        when(mapper.deleteById(any(Long.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.delete(new DeleteSlotRequest(1L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_IN_USE))
                .hasMessage("广告位存在关联数据，不能删除").hasCause(failure);
        var unexpected = new DataIntegrityViolationException("unexpected");
        doThrow(unexpected).when(mapper).deleteById(any(Long.class));
        assertThatThrownBy(() -> service.delete(new DeleteSlotRequest(1L))).isSameAs(unexpected);
    }

    @Test
    void returnsPageRecords() {
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(call -> {
            Page<SlotEntity> page = call.getArgument(0);
            page.setRecords(List.of(slot()));
            page.setTotal(1);
            return page;
        });
        var page = service.pageQuery(new PageSlotRequest(1L, 10L));
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).singleElement().satisfies(row -> assertThat(row.slotCode()).isEqualTo("OLD"));
    }

    private SlotEntity slot() {
        SlotEntity entity = new SlotEntity();
        entity.setId(1L);
        entity.setId(1L);
        entity.setSlotCode("OLD");
        entity.setName("首页");
        entity.setWidth(100);
        entity.setHeight(200);
        entity.setScene("APP");
        entity.setStatus(1);
        return entity;
    }
}
