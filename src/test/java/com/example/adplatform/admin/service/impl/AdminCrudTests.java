package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.util.AdvertiserGuardUtils;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.*;
import com.example.adplatform.admin.entity.*;
import com.example.adplatform.admin.mapper.*;
import com.example.adplatform.admin.request.advertiser.*;
import com.example.adplatform.admin.request.material.*;
import com.example.adplatform.admin.request.rule.*;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminCrudTests {
    @BeforeAll
    static void initializeMetadata() {
        for (Class<?> entity : List.of(AdvertiserEntity.class, MaterialEntity.class, RuleEntity.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entity);
        }
    }

    @Test
    void advertiserUpdateDoesNotWriteStatusAndCanBeRepeated() {
        AdvertiserMapper mapper = mock(AdvertiserMapper.class);
        AdvertiserServiceImpl service = new AdvertiserServiceImpl(mapper, Mappers.getMapper(AdvertiserConverter.class));
        AdvertiserEntity existing = new AdvertiserEntity();
        existing.setId(1L);
        existing.setId(1L);
        existing.setStatus(CommonStatus.ENABLED);
        when(mapper.selectById(any())).thenReturn(existing);
        when(mapper.updateById(any(AdvertiserEntity.class))).thenReturn(1);
        UpdateAdvertiserRequest request = new UpdateAdvertiserRequest(1L, "新名称", "科技", "张三", "a@example.com");
        assertThat(service.update(request).id()).isEqualTo(1L);
        service.update(request);
        ArgumentCaptor<AdvertiserEntity> updates = ArgumentCaptor.forClass(AdvertiserEntity.class);
        verify(mapper, times(2)).updateById(updates.capture());
        assertThat(updates.getAllValues()).allSatisfy(entity -> {
            assertThat(entity.getName()).isEqualTo("新名称");
            assertThat(entity.getStatus()).isNull();
        });
        assertThat(existing.getStatus()).isEqualTo(CommonStatus.ENABLED);
    }

    @Test
    void advertiserUpdateReportsMissingWhenAdvertiserNotFound() {
        AdvertiserMapper mapper = mock(AdvertiserMapper.class);
        AdvertiserServiceImpl service = new AdvertiserServiceImpl(mapper, Mappers.getMapper(AdvertiserConverter.class));
        when(mapper.selectById(any())).thenReturn(null);
        assertThatThrownBy(() -> service.update(new UpdateAdvertiserRequest(1L, null, null, null, null)))
                .hasMessage("广告主不存在");
        verify(mapper, never()).updateById(any(AdvertiserEntity.class));
    }

    @Test
    void advertiserUpdateRejectsDisabledAdvertiser() {
        AdvertiserMapper mapper = mock(AdvertiserMapper.class);
        AdvertiserServiceImpl service = new AdvertiserServiceImpl(mapper, Mappers.getMapper(AdvertiserConverter.class));
        AdvertiserEntity disabled = new AdvertiserEntity();
        disabled.setId(1L);
        disabled.setStatus(CommonStatus.DISABLED);
        when(mapper.selectById(any())).thenReturn(disabled);
        assertThatThrownBy(() -> service.update(new UpdateAdvertiserRequest(1L, "新名称", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("广告主已停用");
        verify(mapper, never()).updateById(any(AdvertiserEntity.class));
    }

    @Test
    void advertiserDeleteReportsMissingOnRepeat() {
        AdvertiserMapper mapper = mock(AdvertiserMapper.class);
        AdvertiserServiceImpl service = new AdvertiserServiceImpl(mapper, Mappers.getMapper(AdvertiserConverter.class));
        when(mapper.deleteById(any(Long.class))).thenReturn(1, 0);
        DeleteAdvertiserRequest request = new DeleteAdvertiserRequest(1L);
        assertThat(service.delete(request).id()).isEqualTo(1L);
        assertThatThrownBy(() -> service.delete(request)).isInstanceOf(BusinessException.class).hasMessage("广告主不存在");
    }

    @Test
    void materialCreateAcceptsExistingReferencesAndRejectsMissingAdvertiser() {
        MaterialMapper mapper = mock(MaterialMapper.class);
        AdvertiserMapper advertisers = mock(AdvertiserMapper.class);
        MaterialServiceImpl service = new MaterialServiceImpl(mapper,
                Mappers.getMapper(MaterialConverter.class), new AdvertiserGuardUtils(advertisers));
        CreateMaterialRequest request = new CreateMaterialRequest(1L, 1L, "素材", "描述", "image", "landing");
        AdvertiserEntity advertiser = new AdvertiserEntity();
        advertiser.setStatus(CommonStatus.ENABLED);
        when(advertisers.selectById(any())).thenReturn(advertiser);
        when(mapper.insert(any(MaterialEntity.class))).thenAnswer(call -> {
            ((MaterialEntity) call.getArgument(0)).setId(1L);
            return 1;
        });
        assertThat(service.create(request).id()).isEqualTo(1L);
        ArgumentCaptor<MaterialEntity> inserted = ArgumentCaptor.forClass(MaterialEntity.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getAuditStatus()).isEqualTo("PENDING");
        assertThat(inserted.getValue().getStatus()).isEqualTo(1);
        when(advertisers.selectById(any())).thenReturn(null);
        assertThatThrownBy(() -> service.create(request)).hasMessage("广告主不存在");
        verify(mapper, times(1)).insert(any(MaterialEntity.class));
    }

    @Test
    void advertiserPageHasNoFiltersAndIncludesRecords() {
        AdvertiserMapper mapper = mock(AdvertiserMapper.class);
        AdvertiserServiceImpl service = new AdvertiserServiceImpl(mapper, Mappers.getMapper(AdvertiserConverter.class));
        AdvertiserEntity entity = new AdvertiserEntity();
        entity.setName("广告主");
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(call -> {
            Page<AdvertiserEntity> page = call.getArgument(0);
            Wrapper<AdvertiserEntity> query = call.getArgument(1);
            assertThat(query.getExpression().getNormal()).isEmpty();
            page.setTotal(1);
            page.setRecords(List.of(entity));
            return page;
        });
        var response = service.pageQuery(new PageAdvertiserRequest(1L, 10L));
        assertThat(response.current()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).singleElement().satisfies(row -> assertThat(row.name()).isEqualTo("广告主"));
    }

    @Test
    void ruleCreateRejectsDatabaseDuplicateInsteadOfOverwriting() {
        RuleMapper mapper = mock(RuleMapper.class);
        RuleConverter converter = mock(RuleConverter.class);
        AdvertiserGuardUtils guard = mock(AdvertiserGuardUtils.class);
        RuleServiceImpl service = new RuleServiceImpl(mapper, converter, guard);
        var request = new CreateRuleRequest(1L, "规则一", null, null, null, null);
        when(converter.createRequestToEntity(request)).thenReturn(new RuleEntity());
        when(mapper.insert(any(RuleEntity.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class).hasMessage("该广告主下已存在同名定向规则");
        verify(guard).requireEnabled(1L);
        verify(mapper, never()).updateById(any(RuleEntity.class));
    }
}
