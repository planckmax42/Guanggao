package com.example.adplatform.admin.config;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.entity.SlotEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminIdContractTests {
    @Test
    void allAdminEntitiesUseOnlyAssignedLongId() throws Exception {
        for (var type : List.of(AdvertiserEntity.class, MaterialEntity.class, PlanEntity.class,
                RuleEntity.class, SlotEntity.class)) {
            var id = type.getDeclaredField("id");
            assertThat(id.getType()).isEqualTo(Long.class);
            assertThat(id.getAnnotation(TableId.class).type()).isEqualTo(IdType.ASSIGN_ID);
            assertThat(type.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
                    .doesNotContain("publicId", "advertiserPublicId", "slotPublicId");
        }
    }
}
