package com.example.adplatform.admin.service;

import com.example.adplatform.admin.dto.CreateAdvertiserRequest;
import com.example.adplatform.admin.vo.AdvertiserVO;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefVO;

public interface AdvertiserService {

    ResourceRefVO create(CreateAdvertiserRequest request);

    PageResponse<AdvertiserVO> pageQuery(long current, long size, String name, Integer status);
}
