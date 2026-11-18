package com.example.adplatform.delivery.service;

import com.example.adplatform.delivery.request.DeliveryRequest;
import com.example.adplatform.delivery.response.AdDeliveryResponse;

public interface AdDeliveryService {

    AdDeliveryResponse deliver(DeliveryRequest request);
}
