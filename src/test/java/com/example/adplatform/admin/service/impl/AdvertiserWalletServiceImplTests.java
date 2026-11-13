package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.AdvertiserWalletConverter;
import com.example.adplatform.admin.entity.AdvertiserWalletEntity;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.mapper.AdvertiserWalletMapper;
import com.example.adplatform.admin.mapper.AdvertiserMapper;
import com.example.adplatform.admin.request.advertiserwallet.*;
import com.example.adplatform.admin.util.AdvertiserGuardUtils;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdvertiserWalletServiceImplTests {
    private final AdvertiserWalletMapper mapper = mock(AdvertiserWalletMapper.class);
    private final AdvertiserMapper advertisers = mock(AdvertiserMapper.class);
    private final AdvertiserWalletServiceImpl service = new AdvertiserWalletServiceImpl(mapper,
            Mappers.getMapper(AdvertiserWalletConverter.class), new AdvertiserGuardUtils(advertisers));

    @Test
    void shouldCreateWithAssignedIdAndDefaults() {
        advertiser(CommonStatus.ENABLED);
        when(mapper.insert(any(AdvertiserWalletEntity.class))).thenAnswer(call -> {
            ((AdvertiserWalletEntity) call.getArgument(0)).setId(1987654321098765432L);
            return 1;
        });
        assertThat(service.create(new CreateAdvertiserWalletRequest(2L)).id()).isEqualTo(1987654321098765432L);
        var inserted = ArgumentCaptor.forClass(AdvertiserWalletEntity.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getAdvertiserId()).isEqualTo(2L);
        assertThat(inserted.getValue().getBalanceMicro()).isZero();
        assertThat(inserted.getValue().getFrozenMicro()).isZero();
    }

    @Test
    void shouldRejectMissingOrDisabledAdvertiser() {
        assertThatThrownBy(() -> service.create(new CreateAdvertiserWalletRequest(2L))).hasMessage("广告主不存在");
        advertiser(CommonStatus.DISABLED);
        assertThatThrownBy(() -> service.create(new CreateAdvertiserWalletRequest(2L))).hasMessage("广告主已停用");
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldTranslateDuplicateAndMissingReferenceOnCreate() {
        advertiser(CommonStatus.ENABLED);
        when(mapper.insert(any(AdvertiserWalletEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> service.create(new CreateAdvertiserWalletRequest(2L))).hasMessage("该广告主已存在钱包");
        when(mapper.insert(any(AdvertiserWalletEntity.class))).thenThrow(integrity(1452));
        assertThatThrownBy(() -> service.create(new CreateAdvertiserWalletRequest(2L))).hasMessage("广告主不存在");
    }

    @Test
    void shouldUpdateOnlyProvidedFields() {
        var existing = existing();
        when(mapper.updateById(any(AdvertiserWalletEntity.class))).thenReturn(1);
        assertThat(service.update(new UpdateAdvertiserWalletRequest(1L, 500L, null)).id()).isEqualTo(1L);
        var updated = ArgumentCaptor.forClass(AdvertiserWalletEntity.class);
        verify(mapper).updateById(updated.capture());
        assertThat(updated.getValue().getId()).isEqualTo(1L);
        assertThat(updated.getValue().getBalanceMicro()).isEqualTo(500L);
        assertThat(updated.getValue().getFrozenMicro()).isNull();
        assertThat(updated.getValue().getAdvertiserId()).isNull();
        assertThat(existing.getAdvertiserId()).isEqualTo(2L);
    }

    @Test
    void shouldSkipRepeatedUnchangedUpdate() {
        var existing = existing();
        existing.setBalanceMicro(500L);
        assertThat(service.update(new UpdateAdvertiserWalletRequest(1L, 500L, null)).id()).isEqualTo(1L);
        assertThat(service.update(new UpdateAdvertiserWalletRequest(1L, 500L, null)).id()).isEqualTo(1L);
        verify(mapper, never()).updateById(any(AdvertiserWalletEntity.class));
    }

    @Test
    void shouldRejectMissingAndConcurrentlyDeletedRecord() {
        assertThatThrownBy(() -> service.update(new UpdateAdvertiserWalletRequest(1L, 500L, null))).hasMessage("钱包不存在");
        existing();
        when(mapper.updateById(any(AdvertiserWalletEntity.class))).thenReturn(0);
        assertThatThrownBy(() -> service.update(new UpdateAdvertiserWalletRequest(1L, 500L, null))).hasMessage("钱包不存在");
    }

    @Test
    void shouldDeleteOnceAndRejectRepeatedDelete() {
        when(mapper.deleteById(1L)).thenReturn(1, 0);
        assertThat(service.delete(new DeleteAdvertiserWalletRequest(1L)).id()).isEqualTo(1L);
        assertThatThrownBy(() -> service.delete(new DeleteAdvertiserWalletRequest(1L)))
                .isInstanceOf(BusinessException.class).hasMessage("钱包不存在");
    }

    @Test
    void shouldTranslateReferencedDeleteButRethrowOtherIntegrityErrors() {
        when(mapper.deleteById(1L)).thenThrow(integrity(1451));
        assertThatThrownBy(() -> service.delete(new DeleteAdvertiserWalletRequest(1L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_IN_USE));
        var failure = integrity(1048);
        doThrow(failure).when(mapper).deleteById(1L);
        assertThatThrownBy(() -> service.delete(new DeleteAdvertiserWalletRequest(1L))).isSameAs(failure);
        advertiser(CommonStatus.ENABLED);
        when(mapper.insert(any(AdvertiserWalletEntity.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.create(new CreateAdvertiserWalletRequest(2L))).isSameAs(failure);
    }

    @Test
    void shouldReturnPageWithConvertedRecords() {
        var row = new AdvertiserWalletEntity();
        row.setId(1L);
        row.setAdvertiserId(2L);
        var page = new Page<AdvertiserWalletEntity>(2, 10, 11);
        page.setRecords(List.of(row));
        when(mapper.selectPage(any(Page.class), any())).thenReturn(page);
        var result = service.pageQuery(new PageAdvertiserWalletRequest(2L, 10L));
        assertThat(result.current()).isEqualTo(2);
        assertThat(result.total()).isEqualTo(11);
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).id()).isEqualTo(1L);
        assertThat(result.records().get(0).advertiserId()).isEqualTo(2L);
    }

    private AdvertiserWalletEntity existing() {
        var existing = new AdvertiserWalletEntity();
        existing.setId(1L);
        existing.setAdvertiserId(2L);
        when(mapper.selectById(1L)).thenReturn(existing);
        return existing;
    }

    private void advertiser(int status) {
        var advertiser = new AdvertiserEntity();
        advertiser.setId(2L);
        advertiser.setStatus(status);
        when(advertisers.selectById(2L)).thenReturn(advertiser);
    }

    private DataIntegrityViolationException integrity(int code) {
        return new DataIntegrityViolationException("constraint", new SQLException("constraint", "23000", code));
    }
}
