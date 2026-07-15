package com.example.adplatform.infra.redis.frequency;

import java.time.LocalDate;

public interface FrequencyRedisService {

    /**
     * 判断同一用户当天看到同一计划的次数是否已经达到上限。
     */
    boolean isViewerPlanFrequencyExceeded(Long viewerId, Long planId, LocalDate statDate, int maxFrequency);

    /**
     * 曝光事件消费成功后，累加同一用户当天看到同一计划的次数。
     */
    void incrementViewerPlanImpression(Long viewerId, Long planId, LocalDate statDate);
}
