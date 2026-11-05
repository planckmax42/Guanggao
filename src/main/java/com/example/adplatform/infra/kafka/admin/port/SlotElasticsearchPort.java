package com.example.adplatform.infra.kafka.admin.port;

public interface SlotElasticsearchPort {
    void SlotElasticSearchUpdate(String oldSlotCode,String newSlotCode);
}
