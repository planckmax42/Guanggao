package com.example.adplatform.delivery.port;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

/** 用户对广告计划的每日曝光频率控制服务。 */
public interface FrequencyControlPort {

    /**
     * 通过一次 multiGet 批量找出已达到曝光上限的计划。
     *
     * @return 已超过当日频控阈值的计划 ID
     */
    Set<Long> findExceededPlans(Long viewerId, Collection<Long> planIds, LocalDate statDate, int maxFrequency);
}
