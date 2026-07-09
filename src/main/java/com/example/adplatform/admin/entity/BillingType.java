package com.example.adplatform.admin.entity;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

public enum BillingType {

    CPC,
    CPM,
    CPA;

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
