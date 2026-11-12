package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.RuleConverter;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.util.AdvertiserGuardUtils;
import com.example.adplatform.admin.mapper.AdvertiserMapper;
import com.example.adplatform.admin.mapper.RuleMapper;
import com.example.adplatform.admin.request.rule.*;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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

class RuleServiceImplTests {
    private final RuleMapper mapper = mock(RuleMapper.class);
    private final AdvertiserMapper advertisers = mock(AdvertiserMapper.class);
    private final RuleConverter converter = Mappers.getMapper(RuleConverter.class);
    private final RuleServiceImpl service = new RuleServiceImpl(mapper, converter, new AdvertiserGuardUtils(advertisers));

    RuleServiceImplTests() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RuleEntity.class);
    }

    @Test
    void renamesOnlyNameAndSkipsRepeatedRename() {
        RuleEntity stored = rule();
        RuleEntity renamed = rule();
        renamed.setName("规则二");
        when(mapper.selectOne(any())).thenReturn(stored, renamed);
        when(mapper.updateById(any(RuleEntity.class))).thenAnswer(call -> {
            RuleEntity update = call.getArgument(0);
            assertThat(update.getId()).isEqualTo(1L);
            assertThat(update.getName()).isEqualTo("规则二");
            assertThat(update.getAdvertiserPublicId()).isNull();
            assertThat(update.getDeviceTypes()).isNull();
            assertThat(update.getGender()).isNull();
            assertThat(update.getAgeMin()).isNull();
            assertThat(update.getAgeMax()).isNull();
            return 1;
        });
        var request = new UpdateRuleRequest("rule_1", "规则二", null, null, null, null);
        assertThat(service.update(request).publicId()).isEqualTo("rule_1");
        service.update(request);
        verify(mapper, times(1)).updateById(any(RuleEntity.class));
        assertThat(stored.getName()).isEqualTo("规则一");
    }

    @Test
    void translatesDuplicateNameOnRename() {
        when(mapper.selectOne(any())).thenReturn(rule());
        when(mapper.updateById(any(RuleEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));
        var request = new UpdateRuleRequest("rule_1", "规则二", null, null, null, null);
        assertThatThrownBy(() -> service.update(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE))
                .hasMessage("该广告主下已存在同名定向规则");
    }

    @Test
    void createsDifferentlyNamedRulesForEnabledAdvertiser() {
        stubAdvertiser(1);
        var request = new CreateRuleRequest("adv_1", "规则一", List.of("ANDROID"), "MALE", 18, 45);
        var first = service.create(request);
        var second = service.create(new CreateRuleRequest("adv_1", "规则二", List.of("ANDROID"), "MALE", 18, 45));
        assertThat(first.publicId()).startsWith("rule_").isNotEqualTo(second.publicId());
        var inserted = ArgumentCaptor.forClass(RuleEntity.class);
        verify(mapper, times(2)).insert(inserted.capture());
        assertThat(inserted.getAllValues()).extracting(RuleEntity::getName).containsExactly("规则一", "规则二");
        assertThat(inserted.getAllValues()).allSatisfy(entity -> {
            assertThat(entity.getAdvertiserPublicId()).isEqualTo("adv_1");
            assertThat(entity.getDeviceTypes()).containsExactly("ANDROID");
        });
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void rejectsMissingAndDisabledAdvertisersBeforeInsert() {
        var request = new CreateRuleRequest("adv_1", "规则一", null, null, null, null);
        assertThatThrownBy(() -> service.create(request)).hasMessage("广告主不存在");
        stubAdvertiser(0);
        assertThatThrownBy(() -> service.create(request)).hasMessage("广告主已停用");
        verifyNoInteractions(mapper);
    }

    @Test
    void translatesCreateConstraintsAndPreservesUnexpectedErrors() {
        stubAdvertiser(1);
        var request = new CreateRuleRequest("adv_1", "规则一", null, null, null, null);
        var missing = integrity(1452);
        when(mapper.insert(any(RuleEntity.class))).thenThrow(missing);
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessException.class)
                .hasMessage("广告主不存在").hasCause(missing);
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insert(any(RuleEntity.class));
        assertThatThrownBy(() -> service.create(request)).hasMessage("该广告主下已存在同名定向规则");
        var unexpected = integrity(1048);
        doThrow(unexpected).when(mapper).insert(any(RuleEntity.class));
        assertThatThrownBy(() -> service.create(request)).isSameAs(unexpected);
    }

    @Test
    void unchangedRulesDoNotWrite() {
        RuleEntity stored = rule();
        stored.setDeviceTypes(List.of("ANDROID"));
        when(mapper.selectOne(any())).thenReturn(stored);
        var request = new UpdateRuleRequest("rule_1", null, List.of("ANDROID"), "MALE", 18, 45);
        assertThat(service.update(request).publicId()).isEqualTo("rule_1");
        service.update(request);
        verify(mapper, never()).updateById(any(RuleEntity.class));
    }

    @Test
    void clearsSuppliedListsAndPreservesOmittedFieldsAndOwner() {
        RuleEntity stored = rule();
        RuleEntity cleared = rule();
        cleared.setDeviceTypes(List.of());
        when(mapper.selectOne(any())).thenReturn(stored, cleared);
        when(mapper.updateById(any(RuleEntity.class))).thenAnswer(call -> {
            RuleEntity update = call.getArgument(0);
            assertThat(update.getId()).isEqualTo(1L);
            assertThat(update.getDeviceTypes()).isEmpty();
            assertThat(update.getName()).isNull();
            assertThat(update.getGender()).isNull();
            assertThat(update.getAgeMin()).isNull();
            assertThat(update.getAgeMax()).isNull();
            assertThat(update.getAdvertiserPublicId()).isNull();
            return 1;
        });
        var request = new UpdateRuleRequest("rule_1", null, List.of(), null, null, null);
        service.update(request);
        service.update(request);
        verify(mapper, times(1)).updateById(any(RuleEntity.class));
        assertThat(stored.getDeviceTypes()).containsExactly("ANDROID");
        assertThat(stored.getAdvertiserPublicId()).isEqualTo("adv_1");
    }

    @Test
    void replacesStoredNullWithEmptyListAndSkipsRepeatedClear() {
        RuleEntity stored = rule();
        stored.setDeviceTypes(null);
        RuleEntity cleared = rule();
        cleared.setDeviceTypes(List.of());
        when(mapper.selectOne(any())).thenReturn(stored, cleared);
        when(mapper.updateById(any(RuleEntity.class))).thenReturn(1);
        var request = new UpdateRuleRequest("rule_1", null, List.of(), null, null, null);
        service.update(request);
        service.update(request);
        var entity = ArgumentCaptor.forClass(RuleEntity.class);
        verify(mapper).updateById(entity.capture());
        assertThat(entity.getValue().getDeviceTypes()).isEmpty();
    }

    @Test
    void validatesMergedAgeBeforeWriting() {
        when(mapper.selectOne(any())).thenReturn(rule());
        assertThatThrownBy(() -> service.update(new UpdateRuleRequest("rule_1", null, null, null, 46, null)))
                .hasMessage("最大年龄必须大于等于最小年龄");
        assertThatThrownBy(() -> service.update(new UpdateRuleRequest("rule_1", null, null, null, null, 17)))
                .hasMessage("最大年龄必须大于等于最小年龄");
        verify(mapper, never()).updateById(any(RuleEntity.class));
    }

    @Test
    void acceptsSingleAgeUpdateWithoutWritingOwnerOrOmittedFields() {
        when(mapper.selectOne(any())).thenReturn(rule());
        when(mapper.updateById(any(RuleEntity.class))).thenAnswer(call -> {
            RuleEntity update = call.getArgument(0);
            assertThat(update.getId()).isEqualTo(1L);
            assertThat(update.getAgeMin()).isEqualTo(20);
            assertThat(update.getAgeMax()).isNull();
            assertThat(update.getAdvertiserPublicId()).isNull();
            assertThat(update.getDeviceTypes()).isNull();
            return 1;
        });
        assertThat(service.update(new UpdateRuleRequest("rule_1", null, null, null, 20, null)).publicId())
                .isEqualTo("rule_1");
    }

    @Test
    void reportsMissingRecordAndZeroUpdateCount() {
        var request = new UpdateRuleRequest("rule_1", null, null, "FEMALE", null, null);
        assertThatThrownBy(() -> service.update(request)).hasMessage("定向规则不存在");
        when(mapper.selectOne(any())).thenReturn(rule());
        when(mapper.updateById(any(RuleEntity.class))).thenReturn(0);
        assertThatThrownBy(() -> service.update(request)).hasMessage("定向规则不存在");
    }

    @Test
    void deletesDirectlyAndReportsMissingOnRepeat() {
        when(mapper.delete(any(Wrapper.class))).thenReturn(1, 0);
        var request = new DeleteRuleRequest("rule_1");
        assertThat(service.delete(request).publicId()).isEqualTo("rule_1");
        assertThatThrownBy(() -> service.delete(request)).hasMessage("定向规则不存在");
        verify(mapper, times(2)).delete(any(Wrapper.class));
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void translatesReferencedRuleDeleteFailure() {
        var failure = integrity(1451);
        when(mapper.delete(any(Wrapper.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.delete(new DeleteRuleRequest("rule_1")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_IN_USE))
                .hasMessage("定向规则存在关联数据，不能删除").hasCause(failure);
        var unexpected = integrity(1048);
        doThrow(unexpected).when(mapper).delete(any(Wrapper.class));
        assertThatThrownBy(() -> service.delete(new DeleteRuleRequest("rule_1"))).isSameAs(unexpected);
    }

    @Test
    void pageIncludesAdvertiserOwnershipAndTargeting() {
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(call -> {
            Page<RuleEntity> page = call.getArgument(0);
            page.setRecords(List.of(rule()));
            page.setTotal(1);
            return page;
        });
        var page = service.pageQuery(new PageRuleRequest(1L, 10L));
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).singleElement().satisfies(row -> {
            assertThat(row.name()).isEqualTo("规则一");
            assertThat(row.advertiserPublicId()).isEqualTo("adv_1");
            assertThat(row.deviceTypes()).containsExactly("ANDROID");
        });
    }

    private void stubAdvertiser(int status) {
        AdvertiserEntity advertiser = new AdvertiserEntity();
        advertiser.setStatus(status);
        when(advertisers.selectOne(any())).thenReturn(advertiser);
    }

    private DataIntegrityViolationException integrity(int code) {
        return new DataIntegrityViolationException("write failed", new SQLException("constraint", "23000", code));
    }

    private RuleEntity rule() {
        RuleEntity rule = new RuleEntity();
        rule.setId(1L);
        rule.setName("规则一");
        rule.initializePublicId("rule_1");
        rule.setAdvertiserPublicId("adv_1");
        rule.setDeviceTypes(List.of("ANDROID"));
        rule.setGender("MALE");
        rule.setAgeMin(18);
        rule.setAgeMax(45);
        return rule;
    }
}
