package com.example.adplatform.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.adplatform.report.entity.AdStatsDailyEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

public interface AdStatsDailyMapper extends BaseMapper<AdStatsDailyEntity> {

    /**
     * 统计表按“日期 + 计划 + 素材 + 广告位”做唯一约束。
     * 如果记录不存在则插入，存在则在原值基础上累加，保证事件上报后可以实时更新报表。
     */
    @Insert("""
            INSERT INTO ad_stats_daily
                (stat_date, campaign_id, creative_id, ad_slot_id,
                 impression_count, click_count, conversion_count, cost_amount,
                 created_at, updated_at)
            VALUES
                (#{statDate}, #{campaignId}, #{creativeId}, #{adSlotId},
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
            @Param("campaignId") Long campaignId,
            @Param("creativeId") Long creativeId,
            @Param("adSlotId") Long adSlotId,
            @Param("impressionCount") long impressionCount,
            @Param("clickCount") long clickCount,
            @Param("conversionCount") long conversionCount,
            @Param("costAmount") long costAmount);

    @Select("""
            SELECT COALESCE(SUM(impression_count), 0)
            FROM ad_stats_daily
            WHERE stat_date = #{statDate}
              AND campaign_id = #{campaignId}
            """)
    long sumImpressionsByCampaign(
            @Param("statDate") LocalDate statDate,
            @Param("campaignId") Long campaignId);

    @Select("""
            SELECT COALESCE(SUM(click_count), 0)
            FROM ad_stats_daily
            WHERE stat_date = #{statDate}
              AND campaign_id = #{campaignId}
            """)
    long sumClicksByCampaign(
            @Param("statDate") LocalDate statDate,
            @Param("campaignId") Long campaignId);

    @Select("""
            SELECT COALESCE(SUM(cost_amount), 0)
            FROM ad_stats_daily
            WHERE stat_date = #{statDate}
              AND campaign_id = #{campaignId}
            """)
    long sumCostByCampaignOnDate(
            @Param("statDate") LocalDate statDate,
            @Param("campaignId") Long campaignId);

    @Select("""
            SELECT COALESCE(SUM(cost_amount), 0)
            FROM ad_stats_daily
            WHERE campaign_id = #{campaignId}
            """)
    long sumCostByCampaign(@Param("campaignId") Long campaignId);
}
