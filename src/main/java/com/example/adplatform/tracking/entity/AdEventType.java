package com.example.adplatform.tracking.entity;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;

public enum AdEventType {

    IMPRESSION,
    CLICK,
    CONVERSION;

    public static AdEventType parse(String value) {
        try {
            return AdEventType.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_VALIDATION_FAILED,
                    "事件类型只能是 IMPRESSION、CLICK 或 CONVERSION");
        }
    }
}
