package com.example.adplatform.search.event.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

import java.time.Instant;

/**
 * ES 广告事件读取模型，一条文档对应 event 表中的一个事件。
 *
 * <p>eventId 同时作为文档 ID，保证 Kafka 至少一次投递时重复索引仍然幂等。日期字段
 * 使用 epoch millis，业务接口边界再按应用时区转换为 LocalDateTime。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Document(indexName = "ad-event", createIndex = false, writeTypeHint = WriteTypeHint.FALSE)
public class AdEventDocument {

    @Id
    private String eventId;
    private String requestId;
    private String eventType;
    private Long planId;
    private Long materialId;
    private Long slotId;
    private Long viewerId;
    private String billingType;
    private boolean charged;
    private Long costAmount;
    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant eventTime;
    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant createdAt;
}
