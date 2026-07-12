package com.example.adplatform.tracking.entity;

/**
 * 扣费流水状态。SUCCESS 表示真实扣费成功，BUDGET_EXHAUSTED 表示本次事件应计费但预算不足。
 */
public enum ChargeStatus {

    SUCCESS,
    BUDGET_EXHAUSTED
}
