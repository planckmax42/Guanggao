package com.example.adplatform.search.candidate.service;

import com.example.adplatform.search.candidate.model.AdCandidateDocument;

import java.util.List;

public record CandidateRecallResult(List<AdCandidateDocument> candidates, String source) {
}
