package com.example.adplatform.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.adplatform.report.entity.DailyReportEntity;
import com.example.adplatform.report.query.PlanDailyMetricRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface DailyReportMapper extends BaseMapper<DailyReportEntity> {

    /**
     * 统计表按“日期 + 计划 + 素材 + 广告位”做唯一约束。
     * 如果记录不存在则插入，存在则在原值基础上累加，保证事件上报后可以实时更新报表。
     */
    @Insert("""
            INSERT INTO daily_report
                (stat_date, plan_id, material_id, slot_id,
                 impression_count, click_count, conversion_count, cost_amount,
                 created_at, updated_at)
            VALUES
                (#{statDate}, #{planId}, #{materialId}, #{slotId},
                 #{impressionCount}, #{clickCount}, #{conversionCount}, #{costAmount},
                 NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                impression_count = impression_count + VALUES(impression_count),
                click_count = click_count + VALUES(click_count),
                conversion_count = conversion_count + VALUES(conversion_count),
                cost_amount = cost_amount + VALUES(cost_amount),
                updated_at = NOW()
            """)
    int upsertIncrement(
            @Param("statDate") LocalDate statDate,
            @Param("planId") Long planId,
            @Param("materialId") Long materialId,
            @Param("slotId") Long slotId,
            @Param("impressionCount") long impressionCount,
            @Param("clickCount") long clickCount,
            @Param("conversionCount") long conversionCount,
            @Param("costAmount") long costAmount);

    @Select("""
            SELECT COALESCE(SUM(impression_count), 0)
            FROM daily_report
            WHERE stat_date = #{statDate}
              AND plan_id = #{planId}
            """)
    long sumImpressionsByPlan(
            @Param("statDate") LocalDate statDate,
            @Param("planId") Long planId);

    @Select("""
            SELECT COALESCE(SUM(click_count), 0)
            FROM daily_report
            WHERE stat_date = #{statDate}
              AND plan_id = #{planId}
            """)
    long sumClicksByPlan(
            @Param("statDate") LocalDate statDate,
            @Param("planId") Long planId);

    @Select("""
            <script>
            SELECT plan_id AS planId,
                   COALESCE(SUM(impression_count), 0) AS impressionCount,
                   COALESCE(SUM(click_count), 0) AS clickCount
            FROM daily_report
            WHERE stat_date = #{statDate}
              AND plan_id IN
              <foreach collection="planIds" item="planId" open="(" separator="," close=")">
                  #{planId}
              </foreach>
            GROUP BY plan_id
            </script>
            """)
    List<PlanDailyMetricRow> selectPlanDailyMetrics(
            @Param("statDate") LocalDate statDate,
            @Param("planIds") List<Long> planIds);

    @Select("""
            SELECT COALESCE(SUM(cost_amount), 0)
            FROM daily_report
            WHERE stat_date = #{statDate}
              AND plan_id = #{planId}
            """)
    long sumCostByPlanOnDate(
            @Param("statDate") LocalDate statDate,
            @Param("planId") Long planId);

    @Select("""
            SELECT COALESCE(SUM(cost_amount), 0)
            FROM daily_report
            WHERE plan_id = #{planId}
            """)
    long sumCostByPlan(@Param("planId") Long planId);
}
