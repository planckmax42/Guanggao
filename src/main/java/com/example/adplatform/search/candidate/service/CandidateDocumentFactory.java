package com.example.adplatform.search.candidate.service;

import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.search.candidate.dto.CandidateSourceRow;
import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

@Component
@RequiredArgsConstructor
public class CandidateDocumentFactory {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private final ObjectMapper objectMapper;

    public AdCandidateDocument from(CandidateSourceRow row) {
        AdCandidateDocument document = new AdCandidateDocument();
        document.setId(String.valueOf(row.getMaterialId()));
        document.setMaterialId(row.getMaterialId());
        document.setPlanId(row.getPlanId());
        document.setUserId(row.getUserId());
        document.setSlotId(row.getSlotId());
        document.setSlotCode(normalize(row.getSlotCode(), String::toUpperCase));
        document.setTitle(row.getTitle());
        document.setDescription(row.getDescription());
        document.setImageUrl(row.getImageUrl());
        document.setLandingPageUrl(row.getLandingPageUrl());
        document.setMaterialStatus(CommonStatus.ENABLED == row.getMaterialStatus() ? "ENABLED" : "DISABLED");
        document.setAuditStatus(row.getAuditStatus());
        document.setPlanStatus(row.getPlanStatus());
        document.setBudgetTotal(row.getBudgetTotal());
        document.setBudgetDaily(row.getBudgetDaily());
        document.setBidPrice(row.getBidPrice());
        document.setBillingType(row.getBillingType());
        document.setStartTime(toInstant(row.getStartTime()));
        document.setEndTime(toInstant(row.getEndTime()));

        List<String> regions = parseList(row.getRegion(), value -> value.toUpperCase(Locale.ROOT));
        document.setRegionAll(regions.isEmpty());
        document.setRegions(regions);
        List<String> devices = parseList(row.getDeviceType(), value -> value.toUpperCase(Locale.ROOT));
        document.setDeviceAll(devices.isEmpty());
        document.setDeviceTypes(devices);
        document.setGenderAll(!StringUtils.hasText(row.getGender()));
        document.setGender(normalize(row.getGender(), value -> value.toUpperCase(Locale.ROOT)));
        document.setAgeAll(row.getAgeMin() == null && row.getAgeMax() == null);
        document.setAgeMin(row.getAgeMin() == null ? 0 : row.getAgeMin());
        document.setAgeMax(row.getAgeMax() == null ? 120 : row.getAgeMax());
        List<String> tags = parseList(row.getUserTags(), value -> value.toLowerCase(Locale.ROOT));
        document.setTagAll(tags.isEmpty());
        document.setTags(tags);
        document.setUpdatedAt(toInstant(row.getUpdatedAt()));
        return document;
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private List<String> parseList(String json, UnaryOperator<String> normalizer) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST).stream()
                    .filter(StringUtils::hasText)
                    .map(normalizer)
                    .distinct()
                    .toList();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid targeting rule JSON", ex);
        }
    }

    private String normalize(String value, UnaryOperator<String> normalizer) {
        return StringUtils.hasText(value) ? normalizer.apply(value) : null;
    }
}
