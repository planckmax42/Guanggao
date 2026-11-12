package com.example.adplatform.admin.enums;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import org.springframework.util.StringUtils;
import java.util.Locale;

public enum BillingType {

    CPC,

    CPM,

    CPA;

    public static final String PATTERN = "CPC|CPM|CPA";

    public static String normalizeInput(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    public static String normalizeOrDefault(String value) {
        if (!StringUtils.hasText(value)) {
            return CPC.name();
        }
        return normalize(value);
    }

    public static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_VALIDATION_FAILED, "计费方式不能为空");
        }
        String normalized = normalizeInput(value);
        try {
            return BillingType.valueOf(normalized).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_VALIDATION_FAILED, "计费方式只能是 CPC、CPM 或 CPA");
        }
    }
}
