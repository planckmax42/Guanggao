package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.converter.MaterialConverter;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.admin.mapper.*;
import com.example.adplatform.admin.request.material.*;
import com.example.adplatform.common.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MaterialServiceImplTests {
    private final MaterialMapper mapper = mock(MaterialMapper.class);
    private final AdvertiserMapper advertisers = mock(AdvertiserMapper.class);
    private final SlotMapper slots = mock(SlotMapper.class);
    private final MaterialServiceImpl service = new MaterialServiceImpl(mapper, slots,
            Mappers.getMapper(MaterialConverter.class), advertisers);

    @Test
    void shouldRejectDuplicateMaterialNameForAdvertiser() {
        stubAdvertiser(CommonStatus.ENABLED);
        stubSlot(CommonStatus.ENABLED);
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
        verifyNoInteractions(slots, mapper);
    }

    @Test
    void shouldRejectDisabledAdvertiserOnCreate() {
        stubAdvertiser(CommonStatus.DISABLED);
        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BusinessException.class).hasMessage("广告主已停用");
        verifyNoInteractions(slots, mapper);
    }

    @Test
    void shouldAllowDisabledSlotOnCreate() {
        stubAdvertiser(CommonStatus.ENABLED);
        stubSlot(CommonStatus.DISABLED);
        service.create(createRequest());
        verify(mapper).insert(any(MaterialEntity.class));
    }

    @Test
    void shouldCreateWhenAdvertiserAndSlotAreEnabled() {
        stubAdvertiser(CommonStatus.ENABLED);
        stubSlot(CommonStatus.ENABLED);
        service.create(createRequest());
        verify(mapper).insert(any(MaterialEntity.class));
    }

    private CreateMaterialRequest createRequest() {
        return new CreateMaterialRequest("adv_1", "slot_1", "素材", "描述", "image", "landing");
    }

    private void stubAdvertiser(int status) {
        AdvertiserEntity advertiser = new AdvertiserEntity();
        advertiser.setStatus(status);
        when(advertisers.selectOne(any())).thenReturn(advertiser);
    }

    private void stubSlot(int status) {
        SlotEntity slot = new SlotEntity();
        slot.setStatus(status);
        when(slots.selectOne(any())).thenReturn(slot);
        when(slots.exists(any())).thenReturn(true);
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
        verifyNoInteractions(slots, advertisers);
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
        stubSlot(CommonStatus.ENABLED);
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
    void shouldAllowDisabledSlotOnUpdate() {
        when(mapper.selectOne(any())).thenReturn(new MaterialEntity());
        stubSlot(CommonStatus.DISABLED);
        var request = new UpdateMaterialRequest("mat_1", "slot_1", "素材", null, null, null);
        when(mapper.updateById(any(MaterialEntity.class))).thenReturn(1);
        service.update(request);
        ArgumentCaptor<MaterialEntity> update = ArgumentCaptor.forClass(MaterialEntity.class);
        verify(mapper).updateById(update.capture());
        assertThat(update.getValue().getSlotPublicId()).isEqualTo("slot_1");
    }

    @Test
    void shouldRejectMissingSlotOnUpdate() {
        when(mapper.selectOne(any())).thenReturn(new MaterialEntity());
        var request = new UpdateMaterialRequest("mat_1", "slot_1", "素材", null, null, null);
        assertThatThrownBy(() -> service.update(request))
                .isInstanceOf(BusinessException.class).hasMessage("广告位不存在");
        verify(mapper, never()).updateById(any(MaterialEntity.class));
    }

    @Test
    void shouldRejectMissingMaterialAndSlot() {
        var request = new UpdateMaterialRequest("mat_1", null, "素材", "描述", "image", "landing");
        assertThatThrownBy(() -> service.update(request)).hasMessage("素材不存在");
        stubAdvertiser(CommonStatus.ENABLED);
        assertThatThrownBy(() -> service.create(new CreateMaterialRequest("adv_1", "slot_1", "素材", "描述", "image", "landing")))
                .hasMessage("广告位不存在");
        verify(mapper, never()).insert(any(MaterialEntity.class));
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
        verifyNoInteractions(slots, advertisers);
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
