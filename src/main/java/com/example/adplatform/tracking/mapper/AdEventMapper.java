package com.example.adplatform.tracking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.adplatform.tracking.entity.AdEventEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface AdEventMapper extends BaseMapper<AdEventEntity> {

    @Select("""
            SELECT COUNT(*)
            FROM ad_event
            WHERE user_id = #{userId}
              AND campaign_id = #{campaignId}
              AND event_type = 'IMPRESSION'
              AND event_time >= #{startTime}
              AND event_time < #{endTime}
            """)
    long countUserCampaignImpressions(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
