package com.example.adplatform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.adplatform.admin.entity.AdvertiserEntity;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AdvertiserMapper extends BaseMapper<AdvertiserEntity> {
    /** 关联列保留原名称，存储目标记录的 publicId；需配套数据库迁移。 */
    @Select("""
            SELECT EXISTS (SELECT 1 FROM plan WHERE advertiser_id = #{publicId})
                OR EXISTS (SELECT 1 FROM material WHERE advertiser_id = #{publicId})
                OR EXISTS (SELECT 1 FROM `rule` WHERE advertiser_id = #{publicId})
                OR EXISTS (SELECT 1 FROM advertiser_assistant WHERE advertiser_id = #{publicId})
            """)
    boolean hasReferences(@Param("publicId") String publicId);

}
