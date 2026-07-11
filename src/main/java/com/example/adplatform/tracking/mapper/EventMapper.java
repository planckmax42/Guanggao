package com.example.adplatform.tracking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.adplatform.tracking.entity.EventEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface EventMapper extends BaseMapper<EventEntity> {

    @Select("""
            SELECT COUNT(*)
            FROM `event`
            WHERE viewer_id = #{viewerId}
              AND plan_id = #{planId}
              AND event_type = 'IMPRESSION'
              AND event_time >= #{startTime}
              AND event_time < #{endTime}
            """)
    long countViewerPlanImpressions(
            @Param("viewerId") Long viewerId,
            @Param("planId") Long planId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
