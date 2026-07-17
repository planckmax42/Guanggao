package com.example.adplatform.delivery.service;

import com.example.adplatform.delivery.request.AdDeliveryRequest;
import com.example.adplatform.delivery.response.AdDeliveryResponse;

public interface AdDeliveryService {

    AdDeliveryResponse deliver(AdDeliveryRequest request);
}
