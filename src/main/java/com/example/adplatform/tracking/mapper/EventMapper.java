package com.example.adplatform.tracking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.adplatform.tracking.entity.EventEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface EventMapper extends BaseMapper<EventEntity> {

    @Update("""
            UPDATE `event`
            SET charged = #{charged},
                cost_amount = #{costAmount}
            WHERE event_id = #{eventId}
            """)
    int updateChargeResult(
            @Param("eventId") String eventId,
            @Param("charged") int charged,
            @Param("costAmount") long costAmount);
}
