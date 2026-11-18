package com.example.adplatform.delivery.portMQ;

import java.util.Collection;
import java.util.Set;

public interface DeliveryStopGuardPort  {

    void stopPlan(Long planId);
    void stopMaterial(Long materialId);
    void stopSlot(Long slotId);

    void resumePlan(Long planId);
    void resumeMaterial(Long materialId);
    void resumeSlot(Long slotId);

    Set<Long> findAllowedPlans(Collection<Long> planIds);
    Set<Long> findAllowedMaterials(Collection<Long> materialIds);
    Set<Long> findAllowedSlots(Collection<Long> slotIds);

    boolean isSlotStopped(String slotCode);
}
