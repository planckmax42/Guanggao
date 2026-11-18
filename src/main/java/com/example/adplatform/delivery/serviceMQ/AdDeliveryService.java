package com.example.adplatform.delivery.serviceMQ;

import com.example.adplatform.delivery.request.DeliveryRequest;
import com.example.adplatform.delivery.response.AdDeliveryResponse;

public interface AdDeliveryService {

    AdDeliveryResponse delivery(DeliveryRequest request);
}
