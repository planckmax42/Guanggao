package com.example.adplatform.infra.debezium.admin;

public record SlotElasticSearchUpdatePayload(String oldSlotCode,String newSlotCode) {
}
