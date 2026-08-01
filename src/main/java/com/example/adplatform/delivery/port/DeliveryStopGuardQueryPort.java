package com.example.adplatform.delivery.port;

import java.util.Collection;
import java.util.Set;

/** 查询配置最终一致窗口内紧急停投标记的端口。 */
public interface DeliveryStopGuardQueryPort {

    Set<Long> findStoppedPlans(Collection<Long> planIds);

    Set<Long> findStoppedMaterials(Collection<Long> materialIds);

    Set<Long> findStoppedSlots(Collection<Long> slotIds);
}
