package com.example.adplatform.tracking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.adplatform.tracking.entity.ChargeRecordEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

public interface ChargeRecordMapper extends BaseMapper<ChargeRecordEntity> {

    @Select("""
            SELECT COALESCE(SUM(amount), 0)
            FROM charge_record
            WHERE plan_id = #{planId}
              AND charge_status = 'SUCCESS'
              AND charge_time >= #{statDate}
              AND charge_time < DATE_ADD(#{statDate}, INTERVAL 1 DAY)
            """)
    long sumSuccessAmountByPlanOnDate(
            @Param("planId") Long planId,
            @Param("statDate") LocalDate statDate);

    @Select("""
            SELECT COALESCE(SUM(amount), 0)
            FROM charge_record
            WHERE plan_id = #{planId}
              AND charge_status = 'SUCCESS'
            """)
    long sumSuccessAmountByPlan(@Param("planId") Long planId);
}
