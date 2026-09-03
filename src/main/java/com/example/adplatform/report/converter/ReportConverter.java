package com.example.adplatform.report.converter;

import com.example.adplatform.report.entity.DailyReportEntity;
import com.example.adplatform.report.response.DailyReportResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReportConverter {

    @Mapping(target = "ctr", expression = "java(divide(entity.getClickCount(), entity.getImpressionCount()))")
    @Mapping(target = "cvr", expression = "java(divide(entity.getConversionCount(), entity.getClickCount()))")
    @Mapping(target = "planPublicId", source = "planPublicId")
    @Mapping(target = "materialPublicId", source = "materialPublicId")
    @Mapping(target = "slotPublicId", source = "slotPublicId")
    DailyReportResponse toDailyReportResponse(
            DailyReportEntity entity,
            String planPublicId,
            String materialPublicId,
            String slotPublicId);

    default double divide(Long numerator, Long denominator) {
        if (denominator == null || denominator <= 0 || numerator == null) {
            return 0D;
        }
        return (double) numerator / denominator;
    }
}
