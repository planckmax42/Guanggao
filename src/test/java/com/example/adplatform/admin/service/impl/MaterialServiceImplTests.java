package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.guard.AdvertiserGuard;

import com.example.adplatform.admin.converter.MaterialConverter;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.admin.mapper.*;
import com.example.adplatform.admin.request.material.*;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MaterialServiceImplTests {
    private final MaterialMapper mapper = mock(MaterialMapper.class);
    private final AdvertiserMapper advertisers = mock(AdvertiserMapper.class);
    private final MaterialServiceImpl service = new MaterialServiceImpl(mapper,
            Mappers.getMapper(MaterialConverter.class), new AdvertiserGuard(advertisers));

    @Test
    void shouldRejectDuplicateMaterialNameForAdvertiser() {
        stubAdvertiser(CommonStatus.ENABLED);
        when(mapper.insert(any(MaterialEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));

        var request = new CreateMaterialRequest("adv_1", "slot_1", "素材", "描述", "image", "landing");
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该广告主下已存在同名素材");
    }

    @Test
    void shouldRejectMissingAdvertiserOnCreate() {
        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BusinessException.class).hasMessage("广告主不存在");
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldRejectDisabledAdvertiserOnCreate() {
        stubAdvertiser(CommonStatus.DISABLED);
        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BusinessException.class).hasMessage("广告主已停用");
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldCreateWithEnabledAdvertiserAndPreserveSlotReference() {
        stubAdvertiser(CommonStatus.ENABLED);
        assertThat(service.create(createRequest()).publicId()).startsWith("mat_");
        var inserted = ArgumentCaptor.forClass(MaterialEntity.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getSlotPublicId()).isEqualTo("slot_1");
    }

    @Test
    void shouldRethrowUnrelatedIntegrityErrorsOnCreateAndUpdate() {
        stubAdvertiser(CommonStatus.ENABLED);
        var failure = new DataIntegrityViolationException("not null violation",
                new SQLException("missing value", "23000", 1048));
        when(mapper.insert(any(MaterialEntity.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.create(createRequest())).isSameAs(failure);
        when(mapper.selectOne(any())).thenReturn(new MaterialEntity());
        when(mapper.updateById(any(MaterialEntity.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.update(
                new UpdateMaterialRequest("mat_1", "slot_1", "素材", null, null, null)))
                .isSameAs(failure);
    }

    @Test
    void shouldKeepDuplicateNameMessageOnUpdate() {
        when(mapper.selectOne(any())).thenReturn(new MaterialEntity());
        when(mapper.updateById(any(MaterialEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> service.update(
                new UpdateMaterialRequest("mat_1", null, "素材", null, null, null)))
                .isInstanceOf(BusinessException.class).hasMessage("该广告主下已存在同名素材");
    }

    private CreateMaterialRequest createRequest() {
        return new CreateMaterialRequest("adv_1", "slot_1", "素材", "描述", "image", "landing");
    }

    private void stubAdvertiser(int status) {
        AdvertiserEntity advertiser = new AdvertiserEntity();
        advertiser.setStatus(status);
        when(advertisers.selectOne(any())).thenReturn(advertiser);
    }

    @Test
    void shouldOnlyUpdateNameWithoutLookingUpOmittedReferences() {
        MaterialEntity existing = new MaterialEntity();
        existing.setId(1L);
        existing.setAuditStatus("APPROVED");
        when(mapper.selectOne(any())).thenReturn(existing);
        when(mapper.updateById(any(MaterialEntity.class))).thenReturn(1);
        service.update(new UpdateMaterialRequest("mat_1", null, "新名称", null, null, null));
        ArgumentCaptor<MaterialEntity> update = ArgumentCaptor.forClass(MaterialEntity.class);
        verify(mapper).updateById(update.capture());
        assertThat(update.getValue().getId()).isEqualTo(1L);
        assertThat(update.getValue().getName()).isEqualTo("新名称");
        assertThat(update.getValue().getAuditStatus()).isEqualTo("PENDING");
        assertThat(update.getValue().getSlotPublicId()).isNull();
        assertThat(update.getValue().getAdvertiserPublicId()).isNull();
        verifyNoInteractions(advertisers);
        verify(mapper).selectOne(any());
    }

    @Test
    void shouldUpdateContentAndSlotWithoutChangingAdvertiserOrStatus() {
        MaterialEntity existing = new MaterialEntity();
        existing.setId(1L);
        existing.setAdvertiserPublicId("adv_original");
        existing.setStatus(0);
        existing.setAuditStatus("REJECTED");
        when(mapper.selectOne(any())).thenReturn(existing);
        var request = new UpdateMaterialRequest("mat_1", "slot_1", "素材", "描述", "image", "landing");
        when(mapper.updateById(any(MaterialEntity.class))).thenReturn(1);
        service.update(request);
        ArgumentCaptor<MaterialEntity> update = ArgumentCaptor.forClass(MaterialEntity.class);
        verify(mapper).updateById(update.capture());
        assertThat(update.getValue().getStatus()).isNull();
        assertThat(update.getValue().getAuditStatus()).isEqualTo("PENDING");
        assertThat(update.getValue().getAdvertiserPublicId()).isNull();
        assertThat(existing.getAdvertiserPublicId()).isEqualTo("adv_original");
        verifyNoInteractions(advertisers);
        assertThat(update.getValue().getSlotPublicId()).isEqualTo("slot_1");
    }

    @Test
    void shouldTranslateMissingSlotConstraintOnUpdate() {
        when(mapper.selectOne(any())).thenReturn(new MaterialEntity());
        var failure = new DataIntegrityViolationException("missing slot",
                new SQLException("foreign key conflict", "23000", 1452));
        when(mapper.updateById(any(MaterialEntity.class))).thenThrow(failure);
        var request = new UpdateMaterialRequest("mat_1", "slot_1", "素材", null, null, null);
        assertThatThrownBy(() -> service.update(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND))
                .hasMessage("广告位不存在").hasCause(failure);
    }

    @Test
    void shouldTranslateMissingReferenceConstraintOnCreate() {
        stubAdvertiser(CommonStatus.ENABLED);
        var failure = new DataIntegrityViolationException("missing slot",
                new SQLException("foreign key conflict", "23000", 1452));
        when(mapper.insert(any(MaterialEntity.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND))
                .hasMessage("关联的广告主或广告位不存在").hasCause(failure);
    }

    @Test
    void shouldRejectMissingMaterialBeforeUpdate() {
        var request = new UpdateMaterialRequest("mat_1", null, "素材", "描述", "image", "landing");
        assertThatThrownBy(() -> service.update(request)).hasMessage("素材不存在");
        verify(mapper, never()).updateById(any(MaterialEntity.class));
    }

    @Test
    void shouldPreserveApprovalWhenResubmittingUnchangedContent() {
        MaterialEntity existing = new MaterialEntity();
        existing.setSlotPublicId("slot_1");
        existing.setName("素材");
        existing.setDescription("描述");
        existing.setImageUrl("image");
        existing.setLandingPageUrl("landing");
        existing.setAuditStatus("APPROVED");
        when(mapper.selectOne(any())).thenReturn(existing);
        var request = new UpdateMaterialRequest("mat_1", "slot_1", "素材", "描述", "image", "landing");

        assertThat(service.update(request).publicId()).isEqualTo("mat_1");
        assertThat(service.update(request).publicId()).isEqualTo("mat_1");
        service.update(new UpdateMaterialRequest("mat_1", null, "素材", null, null, null));

        verify(mapper, never()).updateById(any(MaterialEntity.class));
        verifyNoInteractions(advertisers);
        assertThat(existing.getAuditStatus()).isEqualTo("APPROVED");
    }

    @Test
    void shouldRejectEmptyUpdateThroughBeanValidation() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            String publicId = "mat_" + "a".repeat(32);
            var empty = new UpdateMaterialRequest(publicId, null, null, null, null, null);
            assertThat(validator.validate(empty))
                    .extracting(jakarta.validation.ConstraintViolation::getMessage)
                    .containsExactly("请至少提供一个需要修改的字段");
            var update = new UpdateMaterialRequest(publicId, null, "新名称", null, null, null);
            assertThat(validator.validate(update)).isEmpty();
        }
    }
}
