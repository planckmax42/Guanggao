package com.example.adplatform.admin.guard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.mapper.AdvertiserMapper;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdvertiserGuard {

    private final AdvertiserMapper advertiserMapper;

    public void requireEnabled(String publicId) {
        AdvertiserEntity advertiser = advertiserMapper.selectOne(new LambdaQueryWrapper<AdvertiserEntity>()
                .eq(AdvertiserEntity::getPublicId, publicId));
        if (advertiser == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告主不存在");
        }
        if (advertiser.getStatus() == null || advertiser.getStatus() != CommonStatus.ENABLED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "广告主已停用");
        }
    }
}
