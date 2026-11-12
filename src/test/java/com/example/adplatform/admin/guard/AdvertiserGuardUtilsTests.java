package com.example.adplatform.admin.guard;

import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.mapper.AdvertiserMapper;
import com.example.adplatform.admin.util.AdvertiserGuardUtils;
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

class AdvertiserGuardUtilsTests {

    private final AdvertiserMapper mapper = mock(AdvertiserMapper.class);
    private final AdvertiserGuardUtils guard = new AdvertiserGuardUtils(mapper);

    @Test
    void acceptsEnabledAdvertiserOnRepeatedChecks() {
        AdvertiserEntity advertiser = new AdvertiserEntity();
        advertiser.setStatus(1);
        when(mapper.selectById(any())).thenReturn(advertiser);
        assertThatCode(() -> {
            guard.requireEnabled(1L);
            guard.requireEnabled(1L);
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingAdvertiser() {
        assertThatThrownBy(() -> guard.requireEnabled(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0})
    void rejectsAdvertiserWithoutEnabledStatus(Integer status) {
        AdvertiserEntity advertiser = new AdvertiserEntity();
        advertiser.setStatus(status);
        when(mapper.selectById(any())).thenReturn(advertiser);
        assertThatThrownBy(() -> guard.requireEnabled(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }
}
