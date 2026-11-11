package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.converter.AdvertiserConverter;
import com.example.adplatform.admin.converter.PlanConverter;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.mapper.AdvertiserMapper;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.request.advertiser.UpdateAdvertiserRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanRequest;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PartialUpdateTests {
    private final AdvertiserMapper advertisers = mock(AdvertiserMapper.class);
    private final PlanMapper plans = mock(PlanMapper.class);
    private final AdvertiserServiceImpl advertiserService = new AdvertiserServiceImpl(
            advertisers, Mappers.getMapper(AdvertiserConverter.class));
    private final PlanServiceImpl planService = new PlanServiceImpl(plans, advertisers,
            Mappers.getMapper(PlanConverter.class), mock(SearchOutboxService.class),
            mock(ApplicationEventPublisher.class));

    @Test
    void rejectsEmptyRequestsButAllowsSingleFieldUpdates() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            String advertiserId = "adv_" + "a".repeat(32);
            String planId = "plan_" + "a".repeat(32);
            assertThat(validator.validate(new UpdateAdvertiserRequest(advertiserId, null, null, null, null)))
                    .anyMatch(v -> v.getMessage().equals("请至少提供一个需要修改的字段"));
            assertThat(validator.validate(new UpdatePlanRequest(planId, null, null, null, null, null, null, null)))
                    .anyMatch(v -> v.getMessage().equals("请至少提供一个需要修改的字段"));
            assertThat(validator.validate(new UpdateAdvertiserRequest(advertiserId, "名称", null, null, null))).isEmpty();
            assertThat(validator.validate(new UpdatePlanRequest(planId, "名称", null, null, null, null, null, null))).isEmpty();
        }
    }

    @Test
    void unchangedAdvertiserDoesNotWriteOnRepeatedRequests() {
        AdvertiserEntity existing = advertiser();
        when(advertisers.selectOne(any())).thenReturn(existing);
        var request = new UpdateAdvertiserRequest("adv_1", "广告主", null, null, null);
        assertThat(advertiserService.update(request).publicId()).isEqualTo("adv_1");
        advertiserService.update(request);
        verify(advertisers, never()).updateById(any(AdvertiserEntity.class));
    }

    @Test
    void changedAdvertiserUpdatesOnlyProvidedFieldsAndChecksAffectedRows() {
        when(advertisers.selectOne(any())).thenReturn(advertiser());
        when(advertisers.updateById(any(AdvertiserEntity.class))).thenReturn(1, 0);
        var request = new UpdateAdvertiserRequest("adv_1", "新名称", null, null, null);
        advertiserService.update(request);
        var captured = ArgumentCaptor.forClass(AdvertiserEntity.class);
        verify(advertisers).updateById(captured.capture());
        assertThat(captured.getValue().getId()).isEqualTo(1L);
        assertThat(captured.getValue().getName()).isEqualTo("新名称");
        assertThat(captured.getValue().getIndustry()).isNull();
        assertThatThrownBy(() -> advertiserService.update(request)).hasMessage("广告主不存在");
    }

    @Test
    void unchangedPlanDoesNotWriteOnRepeatedRequests() {
        when(plans.selectOne(any())).thenReturn(plan());
        var request = new UpdatePlanRequest("plan_1", "计划", 100L, null, null, null, null, null);
        assertThat(planService.update(request).publicId()).isEqualTo("plan_1");
        planService.update(request);
        verify(plans, never()).updateById(any(PlanEntity.class));
    }

    @Test
    void rejectsPartialBudgetChangesThatConflictWithStoredValues() {
        when(plans.selectOne(any())).thenReturn(plan());
        assertThatThrownBy(() -> planService.update(new UpdatePlanRequest(
                "plan_1", null, null, 101L, null, null, null, null)))
                .hasMessage("日预算必须小于等于总预算");
        assertThatThrownBy(() -> planService.update(new UpdatePlanRequest(
                "plan_1", null, 5L, null, null, null, null, null)))
                .hasMessage("日预算必须小于等于总预算");
        verify(plans, never()).updateById(any(PlanEntity.class));
    }

    @Test
    void rejectsPartialTimeChangesThatConflictWithStoredValues() {
        PlanEntity existing = plan();
        when(plans.selectOne(any())).thenReturn(existing);
        assertThatThrownBy(() -> planService.update(new UpdatePlanRequest(
                "plan_1", null, null, null, null, null, existing.getEndTime(), null)))
                .hasMessage("结束时间必须晚于开始时间");
        assertThatThrownBy(() -> planService.update(new UpdatePlanRequest(
                "plan_1", null, null, null, null, null, null, existing.getStartTime())))
                .hasMessage("结束时间必须晚于开始时间");
        verify(plans, never()).updateById(any(PlanEntity.class));
    }

    @Test
    void acceptsValidPartialPlanUpdateWithUnchangedName() {
        when(plans.selectOne(any())).thenReturn(plan());
        when(plans.updateById(any(PlanEntity.class))).thenReturn(1);
        planService.update(new UpdatePlanRequest("plan_1", "计划", null, 20L, null, null, null, null));
        var captured = ArgumentCaptor.forClass(PlanEntity.class);
        verify(plans).updateById(captured.capture());
        assertThat(captured.getValue().getId()).isEqualTo(2L);
        assertThat(captured.getValue().getBudgetDaily()).isEqualTo(20L);
        assertThat(captured.getValue().getBudgetTotal()).isNull();
        assertThat(captured.getValue().getStatus()).isNull();
    }

    @Test
    void missingRecordsStillFail() {
        assertThatThrownBy(() -> advertiserService.update(new UpdateAdvertiserRequest(
                "adv_1", "名称", null, null, null))).hasMessage("广告主不存在");
        assertThatThrownBy(() -> planService.update(new UpdatePlanRequest(
                "plan_1", "名称", null, null, null, null, null, null))).hasMessage("广告计划不存在");
    }

    private AdvertiserEntity advertiser() {
        AdvertiserEntity entity = new AdvertiserEntity();
        entity.setId(1L);
        entity.setStatus(1);
        entity.setName("广告主");
        return entity;
    }

    private PlanEntity plan() {
        PlanEntity entity = new PlanEntity();
        entity.setId(2L);
        entity.setName("计划");
        entity.setBudgetTotal(100L);
        entity.setBudgetDaily(10L);
        entity.setStartTime(LocalDateTime.of(2030, 1, 1, 0, 0));
        entity.setEndTime(LocalDateTime.of(2030, 2, 1, 0, 0));
        return entity;
    }
}
