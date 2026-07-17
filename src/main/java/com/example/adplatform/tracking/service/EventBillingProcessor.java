package com.example.adplatform.tracking.service;

import com.example.adplatform.tracking.message.EventMessage;

/** 独立完成广告事件的预算判断和计费流水写入。 */
@FunctionalInterface
public interface EventBillingProcessor {

    void bill(EventMessage message);
}
