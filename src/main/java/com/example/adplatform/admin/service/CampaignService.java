package com.example.adplatform.admin.service;

import com.example.adplatform.admin.dto.CreateCampaignRequest;
import com.example.adplatform.admin.dto.UpdateCampaignRequest;
import com.example.adplatform.admin.vo.CampaignVO;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefVO;

public interface CampaignService {

    ResourceRefVO create(CreateCampaignRequest request);

    CampaignVO update(Long id, UpdateCampaignRequest request);

    CampaignVO online(Long id);

    CampaignVO pause(Long id);

    CampaignVO offline(Long id);

    PageResponse<CampaignVO> pageQuery(long current, long size, Long advertiserId, String status);
}
