package com.example.adplatform.delivery.vo;

public record AdItemVO(
        Long campaignId,
        Long creativeId,
        Long adSlotId,
        String title,
        String description,
        String imageUrl,
        String landingPageUrl,
        Long bidPrice,
        Double score) {
}
