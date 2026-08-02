USE ad_platform;

CREATE TABLE IF NOT EXISTS outbox_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    message_key VARCHAR(128) NOT NULL,
    message_type VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    slotBloomFilterSnapshot VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at DATETIME NULL,
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_pending (slotBloomFilterSnapshot, next_retry_at, id),
    KEY idx_outbox_sent_at (slotBloomFilterSnapshot, sent_at),
    KEY idx_outbox_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO schema_version (version, description)
VALUES ('stage-6-elasticsearch-outbox', 'reliable outbox for candidate Elasticsearch indexing')
ON DUPLICATE KEY UPDATE description = VALUES(description);
