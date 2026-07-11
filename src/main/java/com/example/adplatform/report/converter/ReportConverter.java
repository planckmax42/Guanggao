package com.example.adplatform.report.converter;

import com.example.adplatform.report.entity.DailyReportEntity;
import com.example.adplatform.report.vo.DailyReportVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReportConverter {

    @Mapping(target = "ctr", expression = "java(divide(entity.getClickCount(), entity.getImpressionCount()))")
    @Mapping(target = "cvr", expression = "java(divide(entity.getConversionCount(), entity.getClickCount()))")
    DailyReportVO toDailyReportVO(DailyReportEntity entity);

    default double divide(Long numerator, Long denominator) {
        if (denominator == null || denominator <= 0 || numerator == null) {
            return 0D;
        }
        return (double) numerator / denominator;
    }
}
