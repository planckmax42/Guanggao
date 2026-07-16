package com.example.adplatform.search.candidate.controller;

import com.example.adplatform.common.response.Result;
import com.example.adplatform.search.candidate.service.CandidateIndexManager;
import com.example.adplatform.search.candidate.vo.CandidateRebuildVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供候选索引人工全量重建入口；正常增量同步不需要调用该接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/search/candidates")
public class CandidateIndexAdminController {

    private final CandidateIndexManager candidateIndexManager;

    /** 创建新物理索引并在写入完成后原子切换读写别名。 */
    @PostMapping("/rebuild")
    public Result<CandidateRebuildVO> rebuild() {
        return Result.success(candidateIndexManager.rebuild());
    }
}
