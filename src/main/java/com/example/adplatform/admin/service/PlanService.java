package com.example.adplatform.admin.service;

import com.example.adplatform.admin.dto.CreatePlanRequest;
import com.example.adplatform.admin.dto.UpdatePlanRequest;
import com.example.adplatform.admin.vo.PlanVO;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefVO;

public interface PlanService {

    ResourceRefVO create(CreatePlanRequest request);

    PlanVO update(Long id, UpdatePlanRequest request);

    PlanVO online(Long id);

    PlanVO pause(Long id);

    PlanVO offline(Long id);

    PageResponse<PlanVO> pageQuery(long current, long size, Long userId, String status);
}
