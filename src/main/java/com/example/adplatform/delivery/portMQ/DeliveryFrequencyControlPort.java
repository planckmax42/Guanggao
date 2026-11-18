package com.example.adplatform.delivery.portMQ;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

public interface DeliveryFrequencyControlPort {

    void recordEvent(Long viewerId, Long materialId, LocalDate statDate);

    Set<Long> findAllowedMaterials(Long viewerId, Collection<Long> materialIds, LocalDate statDate, int maxFrequency);
}
