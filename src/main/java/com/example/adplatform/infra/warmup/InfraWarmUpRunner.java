package com.example.adplatform.infra.warmup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 基础设施启动预热的统一入口。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InfraWarmUpRunner implements ApplicationRunner {

    private final SlotWarmUpTask slotWarmUpTask;
    private final BudgetWarmUpTask budgetWarmUpTask;
    private final EventMetadataWarmUpTask eventMetadataWarmUpTask;
    private final ElasticsearchWarmUpTask elasticsearchWarmUpTask;

    @Override
    public void run(ApplicationArguments args) {
        runTask("广告位", slotWarmUpTask::warmUp);
        runTask("预算", budgetWarmUpTask::warmUp);
        runTask("事件元数据", eventMetadataWarmUpTask::warmUp);
        runTask("Elasticsearch", elasticsearchWarmUpTask::warmUp);
    }

    private void runTask(String taskName, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException ex) {
            log.warn("基础设施预热任务失败，已继续执行其他任务，task={}", taskName, ex);
        }
    }
}
