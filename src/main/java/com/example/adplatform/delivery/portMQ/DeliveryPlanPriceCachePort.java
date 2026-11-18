package com.example.adplatform.delivery.portMQ;

import com.example.adplatform.infra.redis.delivery.PlanPrice.PlanPrice;

public interface DeliveryPlanPriceCachePort {
    void writePlanPriceToRedis(PlanPrice planPrice);
}
