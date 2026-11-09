package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.advertiser.CreateAdvertiserRequest;
import com.example.adplatform.admin.response.advertiser.AdvertiserQueryResponse;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.response.advertiser.AdvertiserOperationResponse;

public interface AdvertiserService {

    AdvertiserOperationResponse create(CreateAdvertiserRequest request);

    PageResponse<AdvertiserQueryResponse> pageQuery(long current, long size, Integer status);
}
