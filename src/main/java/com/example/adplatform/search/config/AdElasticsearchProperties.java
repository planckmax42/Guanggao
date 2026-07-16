package com.example.adplatform.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.elasticsearch")
public class AdElasticsearchProperties {

    private boolean enabled = true;
    private final Candidate candidate = new Candidate();
    private final Event event = new Event();
    private final Outbox outbox = new Outbox();

    @Getter
    @Setter
    public static class Candidate {
        private String readAlias = "ad-candidate-read";
        private String writeAlias = "ad-candidate-write";
        private int recallSize = 200;
        private int maxRecallSize = 1000;
        private Duration queryTimeout = Duration.ofMillis(100);
        private Duration rebuildLockTtl = Duration.ofMinutes(10);
    }

    @Getter
    @Setter
    public static class Event {
        private String indexPrefix = "ad-event-";
        private String templateName = "ad-event-template";
        private String lifecyclePolicy = "ad-event-retention-30d";
        private int retentionDays = 30;
        private Duration defaultLookback = Duration.ofHours(24);
        private int maxQueryDays = 31;
    }

    @Getter
    @Setter
    public static class Outbox {
        private long publishDelayMs = 500;
        private long publishInitialDelayMs = 3000;
        private int batchSize = 100;
        private int sentRetentionDays = 7;
    }
}
