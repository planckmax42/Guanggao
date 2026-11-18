package com.example.adplatform.infra.redis.delivery.PlanPrice;

import com.example.adplatform.delivery.portMQ.DeliveryPlanPriceCachePort;
import org.springframework.stereotype.Service;

@Service
public class PlanPriceService implements DeliveryPlanPriceCachePort {
    public void writePlanPriceToRedis(PlanPrice planPrice){
        //此函数向redis写入计费缓存 key（Long）是PlanID value（JSON）是其他字段
    }
}
