package com.example.adplatform.admin.response.advertiser;

public record AdvertiserQueryResponse(
        String publicId,
        String name,
        String industry,
        String contactName,
        String contactEmail,
        Integer status) {
}
