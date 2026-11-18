package com.example.adplatform.infra.redis.delivery.stopguard;

import com.example.adplatform.delivery.portMQ.DeliveryStopGuardPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryStopGuardService implements DeliveryStopGuardPort {

    private static final String STOPPED_PLANS = "ad-platform:stopguard:plans";
    private static final String STOPPED_MATERIALS = "ad-platform:stopguard:materials";
    private static final String STOPPED_SLOTS = "ad-platform:stopguard:slots";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void stopPlan(Long planId) {
        addStopped(STOPPED_PLANS, planId);
    }

    @Override
    public void stopMaterial(Long materialId) {
        addStopped(STOPPED_MATERIALS, materialId);
    }

    @Override
    public void stopSlot(Long slotId) {
        addStopped(STOPPED_SLOTS, slotId);
    }

    @Override
    public void resumePlan(Long planId) {
        removeStopped(STOPPED_PLANS, planId);
    }

    @Override
    public void resumeMaterial(Long materialId) {
        removeStopped(STOPPED_MATERIALS, materialId);
    }

    @Override
    public void resumeSlot(Long slotId) {
        removeStopped(STOPPED_SLOTS, slotId);
    }

    @Override
    public Set<Long> findAllowedPlans(Collection<Long> planIds) {
        return findAllowedIds(STOPPED_PLANS, planIds);
    }

    @Override
    public Set<Long> findAllowedMaterials(Collection<Long> materialIds) {
        return findAllowedIds(STOPPED_MATERIALS, materialIds);
    }

    @Override
    public Set<Long> findAllowedSlots(Collection<Long> slotIds) {
        return findAllowedIds(STOPPED_SLOTS, slotIds);
    }

    public boolean isSlotStopped(String slotCode){
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(STOPPED_SLOTS,slotCode));
    }

    private void addStopped(String key, Long id) {
        stringRedisTemplate.opsForSet().add(key, String.valueOf(id));
    }

    private void removeStopped(String key, Long id) {
        stringRedisTemplate.opsForSet().remove(key, String.valueOf(id));
    }

    private Set<Long> findAllowedIds(String key, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        String[] members = ids.stream().map(String::valueOf).toArray(String[]::new);
        Map<Object, Boolean> results =
                stringRedisTemplate.opsForSet().isMember(key, (Object[]) members);
        return ids.stream()
                .filter(id -> Boolean.FALSE.equals(results.get(id.toString())))
                .collect(Collectors.toSet());
    }
}
