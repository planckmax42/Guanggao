package com.example.adplatform.infra.kafka.tracking;

/** 广告事件的三条独立消费链路。 */
public enum EventConsumerStage {
    ARCHIVE("archive"),
    BILLING("billing"),
    STATISTICS("statistics");

    private final String metricTag;

    EventConsumerStage(String metricTag) {
        this.metricTag = metricTag;
    }

    public String metricTag() {
        return metricTag;
    }
}
