package com.example.adplatform.tracking.entity;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;

/**
 * 广告事件类型，用于事件采集、统计汇总和计费判断。
 */
public enum AdEventType {

    /**
     * 曝光：广告被展示给用户。
     */
    IMPRESSION,

    /**
     * 点击：用户点击广告，CPC 计费模式下会触发扣费。
     */
    CLICK,

    /**
     * 转化：用户完成下单、注册等业务目标，CPA 计费模式下会触发扣费。
     */
    CONVERSION;

    /**
     * 解析接口传入的事件类型，并统一转成枚举值。
     */
    public static AdEventType parse(String value) {
        try {
            return AdEventType.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_VALIDATION_FAILED,
                    "事件类型只能是 IMPRESSION、CLICK 或 CONVERSION");
        }
    }
}
