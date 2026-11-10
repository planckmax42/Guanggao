package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.advertiser.UpdateAdvertiserRequest;
import com.example.adplatform.admin.request.advertiser.DeleteAdvertiserRequest;
import com.example.adplatform.admin.request.advertiser.PageAdvertiserRequest;
import com.example.adplatform.admin.request.advertiser.CreateAdvertiserRequest;
import com.example.adplatform.admin.response.advertiser.AdvertiserQueryResponse;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.response.advertiser.AdvertiserOperationResponse;

public interface AdvertiserService {

    AdvertiserOperationResponse create(CreateAdvertiserRequest request);

    PageResponse<AdvertiserQueryResponse> pageQuery(PageAdvertiserRequest request);

    AdvertiserOperationResponse update(UpdateAdvertiserRequest request);

    AdvertiserOperationResponse delete(DeleteAdvertiserRequest request);
}
