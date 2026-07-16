package com.example.adplatform.search.event.service;

import com.example.adplatform.search.config.AdElasticsearchProperties;
import com.example.adplatform.search.support.ElasticsearchRestSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EventIndexManager {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private final AdElasticsearchProperties properties;
    private final ElasticsearchRestSupport restSupport;

    public void ensureTemplate() throws IOException {
        if (!properties.isEnabled()) {
            return;
        }
        restSupport.put("/_ilm/policy/" + properties.getEvent().getLifecyclePolicy(), Map.of(
                "policy", Map.of("phases", Map.of(
                        "hot", Map.of("actions", Map.of()),
                        "delete", Map.of(
                                "min_age", properties.getEvent().getRetentionDays() + "d",
                                "actions", Map.of("delete", Map.of()))))));

        Map<String, Object> mappings = new LinkedHashMap<>();
        mappings.put("dynamic", "strict");
        mappings.put("properties", eventProperties());
        restSupport.put("/_index_template/" + properties.getEvent().getTemplateName(), Map.of(
                "index_patterns", List.of(properties.getEvent().getIndexPrefix() + "*"),
                "priority", 200,
                "template", Map.of(
                        "settings", Map.of(
                                "number_of_shards", 1,
                                "number_of_replicas", 0,
                                "index.lifecycle.name", properties.getEvent().getLifecyclePolicy()),
                        "mappings", mappings)));
    }

    public String indexName(LocalDate date) {
        return properties.getEvent().getIndexPrefix() + BASIC_DATE.format(date);
    }

    private Map<String, Object> eventProperties() {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String name : List.of("eventId", "requestId", "eventType", "billingType")) {
            fields.put(name, Map.of("type", "keyword"));
        }
        for (String name : List.of("planId", "materialId", "slotId", "viewerId", "costAmount")) {
            fields.put(name, Map.of("type", "long"));
        }
        fields.put("charged", Map.of("type", "boolean"));
        fields.put("eventTime", Map.of("type", "date", "format", "strict_date_optional_time||epoch_millis"));
        fields.put("createdAt", Map.of("type", "date", "format", "strict_date_optional_time||epoch_millis"));
        return fields;
    }
}
