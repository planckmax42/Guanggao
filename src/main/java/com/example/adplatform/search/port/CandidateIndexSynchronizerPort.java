package com.example.adplatform.search.port;

import com.example.adplatform.search.outbox.message.ConfigChangeMessage;

/** 配置变化同步到候选索引的端口。 */
public interface CandidateIndexSynchronizerPort {

    void synchronize(ConfigChangeMessage message);
}
