package com.example.adplatform.search.candidate.service;

import com.example.adplatform.delivery.request.AdDeliveryRequest;
import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
//todo:此部分功能应该迁移至业务层
/**
 * Java 侧定向规则匹配器。
 *
 * <p>它既用于 MySQL 降级召回，也用于 ES 召回后的防御性复核，保证两条路径对地域、
 * 设备、性别、年龄和标签的解释一致。标签采用“至少命中一个”的业务语义。</p>
 */
public final class CandidateTargetingMatcher {

    private CandidateTargetingMatcher() { }

    /** 判断候选是否满足请求画像；缺失的请求画像不能命中受限广告。 */
    public static boolean matches(AdCandidateDocument candidate, AdDeliveryRequest request) {
        if (!candidate.isRegionAll() && !containsIgnoreCase(candidate.getRegions(), request.region())) return false;
        if (!candidate.isDeviceAll() && !containsIgnoreCase(candidate.getDeviceTypes(), request.deviceType())) return false;
        if (!candidate.isGenderAll() && (!StringUtils.hasText(request.gender())
                || !candidate.getGender().equalsIgnoreCase(request.gender()))) return false;
        if (!candidate.isAgeAll() && (request.age() == null
                || request.age() < candidate.getAgeMin() || request.age() > candidate.getAgeMax())) return false;
        if (!candidate.isTagAll()) {
            if (request.tags() == null || request.tags().isEmpty()) return false;
            Set<String> requestTags = new HashSet<>();
            request.tags().forEach(tag -> requestTags.add(tag.toLowerCase(Locale.ROOT)));
            if (candidate.getTags().stream().noneMatch(requestTags::contains)) return false;
        }
        return true;
    }

    private static boolean containsIgnoreCase(java.util.List<String> values, String requestValue) {
        return StringUtils.hasText(requestValue)
                && values != null
                && values.stream().anyMatch(value -> value.equalsIgnoreCase(requestValue));
    }
}
