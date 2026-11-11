package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.converter.MaterialConverter;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.mapper.*;
import com.example.adplatform.admin.request.material.*;
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
    private final MaterialServiceImpl service = new MaterialServiceImpl(mapper, mock(PlanMapper.class), slots,
            Mappers.getMapper(MaterialConverter.class), advertisers);

    @Test
    void shouldOnlyUpdateNameWithoutLookingUpOmittedReferences() {
        MaterialEntity existing = new MaterialEntity();
        existing.setId(1L);
        when(mapper.selectOne(any())).thenReturn(existing);
        when(mapper.update(any(MaterialEntity.class), any())).thenReturn(1);
        service.update(new UpdateMaterialRequest("mat_1", null, "新名称", null, null, null));
        ArgumentCaptor<MaterialEntity> update = ArgumentCaptor.forClass(MaterialEntity.class);
        verify(mapper).update(update.capture(), any());
        assertThat(update.getValue().getName()).isEqualTo("新名称");
        assertThat(update.getValue().getSlotPublicId()).isNull();
        assertThat(update.getValue().getAdvertiserPublicId()).isNull();
        verifyNoInteractions(slots, advertisers);
        verify(mapper, never()).selectOne(any());
    }

    @Test
    void shouldUpdateContentAndSlotWithoutChangingAdvertiserOrStatus() {
        MaterialEntity existing = new MaterialEntity();
        existing.setId(1L);
        existing.setAdvertiserPublicId("adv_original");
        existing.setStatus(0);
        existing.setAuditStatus("REJECTED");
        when(mapper.selectOne(any())).thenReturn(existing);
        when(slots.exists(any())).thenReturn(true);
        var request = new UpdateMaterialRequest("mat_1", "slot_1", "素材", "描述", "image", "landing");
        when(mapper.update(any(MaterialEntity.class), any())).thenReturn(1);
        service.update(request);
        ArgumentCaptor<MaterialEntity> update = ArgumentCaptor.forClass(MaterialEntity.class);
        verify(mapper).update(update.capture(), any());
        assertThat(update.getValue().getStatus()).isNull();
        assertThat(update.getValue().getAuditStatus()).isNull();
        assertThat(update.getValue().getAdvertiserPublicId()).isNull();
        assertThat(existing.getAdvertiserPublicId()).isEqualTo("adv_original");
        verifyNoInteractions(advertisers);
        assertThat(update.getValue().getSlotPublicId()).isEqualTo("slot_1");
    }

    @Test
    void shouldRejectMissingMaterialAndSlot() {
        var request = new UpdateMaterialRequest("mat_1", null, "素材", "描述", "image", "landing");
        assertThatThrownBy(() -> service.update(request)).hasMessage("素材不存在");
        when(advertisers.exists(any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CreateMaterialRequest("adv_1", "slot_1", "素材", "描述", "image", "landing")))
                .hasMessage("广告位不存在");
        verify(mapper, never()).insert(any(MaterialEntity.class));
        verify(mapper).update(any(MaterialEntity.class), any());
    }
}
