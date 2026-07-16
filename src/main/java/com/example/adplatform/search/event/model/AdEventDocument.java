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
