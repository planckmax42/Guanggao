package com.example.adplatform.search.candidate.service;

import com.example.adplatform.search.candidate.model.AdCandidateDocument;

import java.util.List;

/**
 * 一次粗召回的结果。
 *
 * @param candidates 候选文档
 * @param source 实际召回来源，用于 Micrometer 指标区分主链路和降级流量
 */
public record CandidateRecallResult(List<AdCandidateDocument> candidates, String source) {
}
