package com.example.adplatform.search.candidate.controller;

import com.example.adplatform.common.response.Result;
import com.example.adplatform.search.candidate.service.CandidateIndexManager;
import com.example.adplatform.search.candidate.vo.CandidateRebuildVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/search/candidates")
public class CandidateIndexAdminController {

    private final CandidateIndexManager candidateIndexManager;

    @PostMapping("/rebuild")
    public Result<CandidateRebuildVO> rebuild() {
        return Result.success(candidateIndexManager.rebuild());
    }
}
