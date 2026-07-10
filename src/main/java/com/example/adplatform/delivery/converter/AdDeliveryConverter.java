package com.example.adplatform.delivery.converter;

import com.example.adplatform.admin.entity.CampaignEntity;
import com.example.adplatform.admin.entity.CreativeEntity;
import com.example.adplatform.delivery.vo.AdItemVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdDeliveryConverter {

    @Mapping(target = "campaignId", source = "campaign.id")
    @Mapping(target = "creativeId", source = "creative.id")
    @Mapping(target = "adSlotId", source = "creative.adSlotId")
    @Mapping(target = "title", source = "creative.title")
    @Mapping(target = "description", source = "creative.description")
    @Mapping(target = "imageUrl", source = "creative.imageUrl")
    @Mapping(target = "landingPageUrl", source = "creative.landingPageUrl")
    @Mapping(target = "bidPrice", source = "campaign.bidPrice")
    AdItemVO toAdItemVO(CreativeEntity creative, CampaignEntity campaign, double score);
}
