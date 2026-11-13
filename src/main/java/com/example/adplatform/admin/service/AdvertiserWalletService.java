package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.advertiserwallet.UpdateAdvertiserWalletRequest;
import com.example.adplatform.admin.request.advertiserwallet.DeleteAdvertiserWalletRequest;
import com.example.adplatform.admin.request.advertiserwallet.PageAdvertiserWalletRequest;
import com.example.adplatform.admin.request.advertiserwallet.CreateAdvertiserWalletRequest;
import com.example.adplatform.admin.response.advertiserwallet.AdvertiserWalletQueryResponse;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.response.advertiserwallet.AdvertiserWalletOperationResponse;

public interface AdvertiserWalletService {

    AdvertiserWalletOperationResponse create(CreateAdvertiserWalletRequest request);

    PageResponse<AdvertiserWalletQueryResponse> pageQuery(PageAdvertiserWalletRequest request);

    AdvertiserWalletOperationResponse update(UpdateAdvertiserWalletRequest request);

    AdvertiserWalletOperationResponse delete(DeleteAdvertiserWalletRequest request);
}
