package com.example.adplatform.infra.elasticsearch.delivery.Candidate;

import org.springframework.data.elasticsearch.core.mapping.PropertyValueConverter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 保持候选文档的本地时间与现有 ES 毫秒时间戳兼容。 */
public class LocalDateTimeEpochMillisConverter implements PropertyValueConverter {

    @Override
    public Object write(Object value) {
        return ((LocalDateTime) value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @Override
    public Object read(Object value) {
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(Long.parseLong(value.toString())), ZoneId.systemDefault());
    }
}
