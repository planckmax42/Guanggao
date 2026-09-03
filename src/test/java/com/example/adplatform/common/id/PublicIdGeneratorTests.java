package com.example.adplatform.common.id;

import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.entity.UserEntity;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicIdGeneratorTests {

    @Test
    void shouldGenerateTypedRandomUuidIdentifiers() {
        String first = PublicIdGenerator.generate(PublicIdGenerator.SLOT_PREFIX);
        String second = PublicIdGenerator.generate(PublicIdGenerator.SLOT_PREFIX);

        assertThat(first).matches("^slot_[0-9a-f]{32}$");
        assertThat(second).matches("^slot_[0-9a-f]{32}$");
        assertThat(first).isNotEqualTo(second);
        assertThat(PublicIdGenerator.generate(PublicIdGenerator.EVENT_PREFIX))
                .matches(PublicIdGenerator.EVENT_PATTERN);
    }

    @Test
    void shouldRejectUnknownPrefix() {
        assertThatThrownBy(() -> PublicIdGenerator.generate("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void externallyAddressableEntitiesShouldNotExposePublicIdSetter() {
        List<Class<?>> entityTypes = List.of(
                UserEntity.class,
                SlotEntity.class,
                PlanEntity.class,
                MaterialEntity.class,
                RuleEntity.class);

        entityTypes.forEach(type -> assertThat(Arrays.stream(type.getMethods())
                .noneMatch(method -> method.getName().equals("setPublicId")))
                .as(type.getSimpleName())
                .isTrue());
    }

    @Test
    void initializedPublicIdShouldNotBeReplaceable() {
        SlotEntity entity = new SlotEntity();
        entity.initializePublicId("slot_00000000000000000000000000000001");

        assertThatThrownBy(() -> entity.initializePublicId(
                "slot_00000000000000000000000000000002"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(entity.getPublicId())
                .isEqualTo("slot_00000000000000000000000000000001");
    }
}
