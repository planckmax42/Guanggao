package com.example.adplatform.infra.redis.delivery.PlanPrice;

public record PlanPrice(
   Long planId,
   String billingType,
   Long bidPrice
) {}
