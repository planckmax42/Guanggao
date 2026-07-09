package com.example.adplatform.delivery.vo;

import java.util.List;

public record AdDeliveryResponse(
        String requestId,
        Integer totalCandidates,
        Integer returnedCount,
        List<AdItemVO> ads) {
}
