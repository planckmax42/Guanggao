package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.CreateAdvertiserRequest;
import com.example.adplatform.admin.response.AdvertiserResponse;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;

public interface AdvertiserService {

    ResourceRefResponse create(CreateAdvertiserRequest request);

    PageResponse<AdvertiserResponse> pageQuery(long current, long size, String name, Integer status);
}
