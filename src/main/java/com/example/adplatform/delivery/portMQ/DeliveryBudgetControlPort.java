package com.example.adplatform.delivery.portMQ;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

public interface DeliveryBudgetControlPort {

    void recordSnapshot(Long planId, LocalDate statDate, long dailyLimit, long dailySpent, long totalLimit, long totalSpent);

    Set<Long> findAllowedPlans(Collection<Long> planIds, LocalDate statDate);
}
