package com.example.adplatform.search.candidate.controller;

import com.example.adplatform.common.response.Result;
import com.example.adplatform.search.candidate.response.CandidateRebuildResponse;
import com.example.adplatform.search.port.CandidateIndexRebuildPort;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供候选索引人工全量重建入口；正常增量同步不需要调用该接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/platform/search/candidates")
public class CandidateIndexManagerController {

    private final CandidateIndexRebuildPort candidateIndexRebuildPort;

    /** 创建新物理索引并在写入完成后原子切换读写别名。 */
    @PostMapping("/rebuild")
    public Result<CandidateRebuildResponse> rebuild() {
        candidateIndexRebuildPort.regularRebuild();//todo:此方法会抛出异常 应该在全局异常捕获器里面处理掉
        return Result.success();
    }
}
