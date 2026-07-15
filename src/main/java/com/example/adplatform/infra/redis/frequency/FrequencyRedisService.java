package com.example.adplatform.infra.redis.frequency;

import java.time.LocalDate;

/** 用户对广告计划的每日曝光频率控制服务。 */
public interface FrequencyRedisService {

    /**
     * 判断同一用户当天看到同一计划的次数是否已经达到上限。
     *
     * @param viewerId 用户标识
     * @param planId 广告计划标识
     * @param statDate 统计日期
     * @param maxFrequency 允许的最大曝光次数
     * @return 当前次数已达上限时返回 {@code true}
     */
    boolean isViewerPlanFrequencyExceeded(Long viewerId, Long planId, LocalDate statDate, int maxFrequency);

    /**
     * 曝光事件消费成功后，累加同一用户当天看到同一计划的次数。
     *
     * @param viewerId 用户标识
     * @param planId 广告计划标识
     * @param statDate 统计日期
     */
    void incrementViewerPlanImpression(Long viewerId, Long planId, LocalDate statDate);
}
