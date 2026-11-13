package com.example.adplatform.admin.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.example.adplatform.admin.entity.*;
import com.example.adplatform.admin.mapper.*;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminAssignedIdMapperTests {
    @Test
    void insertsAllAdminEntitiesWithAssignedIdAndNoPublicIdColumn() throws Exception {
        var types = List.of(AdvertiserEntity.class, MaterialEntity.class, PlanEntity.class, RuleEntity.class,
                SlotEntity.class, AdvertiserAssistantEntity.class, AdvertiserWalletEntity.class);
        var mappers = List.of(AdvertiserMapper.class, MaterialMapper.class, PlanMapper.class, RuleMapper.class,
                SlotMapper.class, AdvertiserAssistantMapper.class, AdvertiserWalletMapper.class);
        var generator = new DefaultIdentifierGenerator(3, 4);
        for (int i = 0; i < types.size(); i++) {
            var configuration = new MybatisConfiguration();
            GlobalConfigUtils.getGlobalConfig(configuration).setIdentifierGenerator(generator);
            configuration.addMapper(mappers.get(i));
            var entity = types.get(i).getConstructor().newInstance();
            var meta = configuration.newMetaObject(entity);
            if (meta.hasSetter("advertiserId")) meta.setValue("advertiserId", 1987654321098765432L);
            if (meta.hasSetter("slotId")) meta.setValue("slotId", 1987654321098765433L);
            var statement = configuration.getMappedStatement(mappers.get(i).getName() + ".insert");
            var sql = statement.getBoundSql(entity);
            assertThat(sql.getSql()).doesNotContain("public_id");
            assertThat(meta.getValue("id")).isNull();
            var jdbc = mock(PreparedStatement.class);
            configuration.newParameterHandler(statement, entity, sql).setParameters(jdbc);
            long id = (Long) meta.getValue("id");
            assertThat(id).isPositive();
            assertThat((id >> 12) & 31).isEqualTo(3);
            for (int j = 0; j < sql.getParameterMappings().size(); j++) {
                String property = sql.getParameterMappings().get(j).getProperty();
                if (property.equals("id") || property.equals("advertiserId") || property.equals("slotId")) {
                    verify(jdbc).setLong(j + 1, (Long) meta.getValue(property));
                }
            }
        }
    }
}
