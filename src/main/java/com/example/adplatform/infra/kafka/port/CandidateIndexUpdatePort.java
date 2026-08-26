package com.example.adplatform.infra.kafka.port;

import com.example.adplatform.search.outbox.message.ConfigChangeMessage;

public interface CandidateIndexUpdatePort {
    void update(ConfigChangeMessage message);
}
