package com.example.adplatform.admin.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdvertiserConverterTests {

    private final AdvertiserConverter converter = Mappers.getMapper(AdvertiserConverter.class);

    @Test
    void shouldConvertMybatisPlusPageAndPreservePaginationMetadata() {
        AdvertiserEntity entity = new AdvertiserEntity();
        entity.initializePublicId("adv_1");
        entity.setName("广告主");
        Page<AdvertiserEntity> page = new Page<>(2, 10, 25);
        page.setRecords(List.of(entity));

        var response = converter.pageToResponse(page);

        assertThat(response.current()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.total()).isEqualTo(25);
        assertThat(response.pages()).isEqualTo(3);
        assertThat(response.records()).containsExactly(converter.entityToQueryResponse(entity));
    }

    @Test
    void shouldConvertEmptyMybatisPlusPage() {
        var response = converter.pageToResponse(new Page<>(1, 20, 0));

        assertThat(response.current()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.total()).isZero();
        assertThat(response.pages()).isZero();
        assertThat(response.records()).isEmpty();
    }
}
