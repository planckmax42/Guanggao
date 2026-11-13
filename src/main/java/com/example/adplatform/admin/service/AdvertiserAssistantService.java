package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.advertiserassistant.UpdateAdvertiserAssistantRequest;
import com.example.adplatform.admin.request.advertiserassistant.DeleteAdvertiserAssistantRequest;
import com.example.adplatform.admin.request.advertiserassistant.PageAdvertiserAssistantRequest;
import com.example.adplatform.admin.request.advertiserassistant.CreateAdvertiserAssistantRequest;
import com.example.adplatform.admin.response.advertiserassistant.AdvertiserAssistantQueryResponse;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.response.advertiserassistant.AdvertiserAssistantOperationResponse;

public interface AdvertiserAssistantService {

    AdvertiserAssistantOperationResponse create(CreateAdvertiserAssistantRequest request);

    PageResponse<AdvertiserAssistantQueryResponse> pageQuery(PageAdvertiserAssistantRequest request);

    AdvertiserAssistantOperationResponse update(UpdateAdvertiserAssistantRequest request);

    AdvertiserAssistantOperationResponse delete(DeleteAdvertiserAssistantRequest request);
}
