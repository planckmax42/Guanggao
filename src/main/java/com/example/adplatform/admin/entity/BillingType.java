package com.example.adplatform.admin.entity;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

/**
 * 广告计费方式，决定哪类事件会产生扣费。
 */
public enum BillingType {

    /**
     * Cost Per Click：按点击扣费，只有 CLICK 事件会消耗预算。
     */
    CPC,

    /**
     * Cost Per Mille：按千次曝光扣费，每累计 1000 次曝光扣一次出价。
     */
    CPM,

    /**
     * Cost Per Action：按转化扣费，只有 CONVERSION 事件会消耗预算。
     */
    CPA;

    /**
     * 将接口传入的计费方式标准化；未传时默认使用 CPC。
     */
    public static String normalizeOrDefault(String value) {
        if (!StringUtils.hasText(value)) {
            return CPC.name();
        }
        String normalized = value.trim().toUpperCase();
        try {
            return BillingType.valueOf(normalized).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_VALIDATION_FAILED, "计费方式只能是 CPC、CPM 或 CPA");
        }
    }
}
