package com.example.adplatform.admin.guard;

import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.mapper.AdvertiserMapper;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdvertiserGuardTests {

    private final AdvertiserMapper mapper = mock(AdvertiserMapper.class);
    private final AdvertiserGuard guard = new AdvertiserGuard(mapper);

    @Test
    void acceptsEnabledAdvertiserOnRepeatedChecks() {
        AdvertiserEntity advertiser = new AdvertiserEntity();
        advertiser.setStatus(1);
        when(mapper.selectOne(any())).thenReturn(advertiser);
        assertThatCode(() -> {
            guard.requireEnabled("adv_1");
            guard.requireEnabled("adv_1");
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingAdvertiser() {
        assertThatThrownBy(() -> guard.requireEnabled("adv_1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0})
    void rejectsAdvertiserWithoutEnabledStatus(Integer status) {
        AdvertiserEntity advertiser = new AdvertiserEntity();
        advertiser.setStatus(status);
        when(mapper.selectOne(any())).thenReturn(advertiser);
        assertThatThrownBy(() -> guard.requireEnabled("adv_1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }
}
