package com.example.adplatform.delivery.service;

import com.example.adplatform.delivery.dto.AdDeliveryRequest;
import com.example.adplatform.delivery.vo.AdDeliveryResponse;

public interface AdDeliveryService {

    AdDeliveryResponse deliver(AdDeliveryRequest request);
}
