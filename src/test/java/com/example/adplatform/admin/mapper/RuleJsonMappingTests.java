package com.example.adplatform.admin.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.example.adplatform.admin.converter.RuleConverter;
import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.util.AdvertiserGuardUtils;
import com.example.adplatform.admin.request.rule.UpdateRuleRequest;
import com.example.adplatform.admin.service.impl.RuleServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuleJsonMappingTests {

    @Test
    void bindsInsertedDeviceListAsJson() throws Exception {
        var configuration = new MybatisConfiguration();
        configuration.addMapper(RuleMapper.class);
        com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.getGlobalConfig(configuration)
                .setIdentifierGenerator(new com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator(1, 1));
        var entity = new RuleEntity();
        entity.setDeviceTypes(List.of("ANDROID", "IOS"));
        var statement = configuration.getMappedStatement(RuleMapper.class.getName() + ".insert");
        var boundSql = statement.getBoundSql(entity);
        var jdbc = mock(PreparedStatement.class);
        configuration.newParameterHandler(statement, entity, boundSql).setParameters(jdbc);
        verify(jdbc).setString(jsonParameterIndex(boundSql), "[\"ANDROID\",\"IOS\"]");
    }

    @Test
    void bindsUpdatedDeviceListAndEmptyListAsJson() throws Exception {
        var configuration = new MybatisConfiguration();
        configuration.addMapper(RuleMapper.class);
        var mapper = mock(RuleMapper.class);
        var stored = new RuleEntity();
        stored.setId(1L);
        stored.setDeviceTypes(List.of("ANDROID"));
        when(mapper.selectById(any())).thenReturn(stored);
        when(mapper.updateById(any(RuleEntity.class))).thenReturn(1);
        var service = new RuleServiceImpl(mapper, Mappers.getMapper(RuleConverter.class), mock(AdvertiserGuardUtils.class));
        service.update(new UpdateRuleRequest(1L, null, List.of("IOS"), null, null, null));
        var entity = ArgumentCaptor.forClass(RuleEntity.class);
        verify(mapper).updateById(entity.capture());
        var params = Map.of("et", entity.getValue());
        var statement = configuration.getMappedStatement(RuleMapper.class.getName() + ".updateById");
        var boundSql = statement.getBoundSql(params);
        assertThat(boundSql.getSql()).contains("device_type=")
                .doesNotContain("name=", "gender=", "age_min=", "age_max=", "advertiser_id=");
        var jdbc = mock(PreparedStatement.class);
        configuration.newParameterHandler(statement, params, boundSql).setParameters(jdbc);
        verify(jdbc).setString(jsonParameterIndex(boundSql), "[\"IOS\"]");

        org.mockito.Mockito.clearInvocations(mapper);
        service.update(new UpdateRuleRequest(1L, null, List.of(), null, null, null));
        verify(mapper).updateById(entity.capture());
        params = Map.of("et", entity.getValue());
        boundSql = statement.getBoundSql(params);
        jdbc = mock(PreparedStatement.class);
        configuration.newParameterHandler(statement, params, boundSql).setParameters(jdbc);
        verify(jdbc).setString(jsonParameterIndex(boundSql), "[]");
    }

    private int jsonParameterIndex(BoundSql boundSql) {
        for (int i = 0; i < boundSql.getParameterMappings().size(); i++) {
            if (boundSql.getParameterMappings().get(i).getTypeHandler() instanceof JacksonTypeHandler) {
                return i + 1;
            }
        }
        throw new AssertionError("device_type 参数未绑定 JacksonTypeHandler");
    }

    @Test
    void readsJsonDeviceArrayThroughEntityResultMap() throws Exception {
        var configuration = new MybatisConfiguration();
        var table = TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RuleEntity.class);
        assertThat(table.getResultMap()).isNotNull();
        var mapping = configuration.getResultMap(table.getResultMap()).getResultMappings().stream()
                .filter(field -> field.getColumn().equals("device_type")).findFirst().orElseThrow();
        assertThat(mapping.getProperty()).isEqualTo("deviceTypes");
        assertThat(mapping.getTypeHandler()).isInstanceOf(JacksonTypeHandler.class);
        var result = mock(ResultSet.class);
        when(result.getString("device_type")).thenReturn("[ \"ANDROID\", \"IOS\" ]", null, "[]");
        assertThat(mapping.getTypeHandler().getResult(result, "device_type")).isEqualTo(List.of("ANDROID", "IOS"));
        assertThat(mapping.getTypeHandler().getResult(result, "device_type")).isNull();
        assertThat(mapping.getTypeHandler().getResult(result, "device_type")).isEqualTo(List.of());
    }
}
