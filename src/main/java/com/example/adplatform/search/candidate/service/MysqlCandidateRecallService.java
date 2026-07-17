package com.example.adplatform.search.candidate.service;

import com.example.adplatform.delivery.request.AdDeliveryRequest;
import com.example.adplatform.search.candidate.mapper.CandidateSourceMapper;
import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ES 不可用时的 MySQL 保底召回。
 *
 * <p>该路径不是正常流量入口，因此优先保证语义正确：先按广告位查询可投放配置，
 * 再复用与 ES 主链路一致的 Java 定向匹配器。</p>
 */
@Service
@RequiredArgsConstructor
public class MysqlCandidateRecallService {

    private final CandidateSourceMapper sourceMapper;
    private final CandidateDocumentFactory documentFactory;

    /** 从 MySQL 真实数据源构造并过滤候选快照。 */
    public List<AdCandidateDocument> recall(AdDeliveryRequest request) {
        return sourceMapper.selectEligibleBySlotCode(request.slotCode()).stream()
                .map(documentFactory::from)
                .filter(candidate -> CandidateTargetingMatcher.matches(candidate, request))
                .toList();
    }
}
