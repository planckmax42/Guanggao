package com.example.adplatform.admin.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
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
import java.sql.Types;
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
        var entity = new RuleEntity();
        entity.setDeviceTypes(List.of("ANDROID", "IOS"));
        var statement = configuration.getMappedStatement(RuleMapper.class.getName() + ".insert");
        var boundSql = statement.getBoundSql(entity);
        var jdbc = mock(PreparedStatement.class);
        configuration.newParameterHandler(statement, entity, boundSql).setParameters(jdbc);
        verify(jdbc).setString(jsonParameterIndex(boundSql), "[\"ANDROID\",\"IOS\"]");
    }

    @Test
    void bindsUpdatedDeviceListAsJsonAndEmptyListAsSqlNull() throws Exception {
        var configuration = new MybatisConfiguration();
        configuration.addMapper(RuleMapper.class);
        var mapper = mock(RuleMapper.class);
        var stored = new RuleEntity();
        stored.setId(1L);
        stored.setDeviceTypes(List.of("ANDROID"));
        when(mapper.selectOne(any())).thenReturn(stored);
        when(mapper.update(any(RuleEntity.class), any())).thenReturn(1);
        var service = new RuleServiceImpl(mapper, Mappers.getMapper(RuleConverter.class), mock(AdvertiserGuardUtils.class));
        service.update(new UpdateRuleRequest("rule_1", null, List.of("IOS"), null, null, null));
        var entity = ArgumentCaptor.forClass(RuleEntity.class);
        var wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(entity.capture(), wrapper.capture());
        var params = Map.of("et", entity.getValue(), "ew", wrapper.getValue());
        var statement = configuration.getMappedStatement(RuleMapper.class.getName() + ".update");
        var boundSql = statement.getBoundSql(params);
        var jdbc = mock(PreparedStatement.class);
        configuration.newParameterHandler(statement, params, boundSql).setParameters(jdbc);
        verify(jdbc).setString(jsonParameterIndex(boundSql), "[\"IOS\"]");

        org.mockito.Mockito.clearInvocations(mapper);
        service.update(new UpdateRuleRequest("rule_1", null, List.of(), null, null, null));
        verify(mapper).update(entity.capture(), wrapper.capture());
        params = Map.of("et", entity.getValue(), "ew", wrapper.getValue());
        boundSql = statement.getBoundSql(params);
        jdbc = mock(PreparedStatement.class);
        configuration.newParameterHandler(statement, params, boundSql).setParameters(jdbc);
        verify(jdbc).setNull(jsonParameterIndex(boundSql), Types.VARCHAR);
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
