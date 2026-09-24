package com.example.adplatform.infra.debezium.admin;

public record SlotCacheUpdatePayload(long Id,String oldSlotCode,String newSlotCode) {
}
