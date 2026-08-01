package com.example.adplatform.infra.warmup;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InfraWarmUpRunnerTests {

    @Test
    void shouldRunAllWarmUpTasksInOrder() throws Exception {
        SlotWarmUpTask slotTask = mock(SlotWarmUpTask.class);
        BudgetWarmUpTask budgetTask = mock(BudgetWarmUpTask.class);
        EventMetadataWarmUpTask eventMetadataTask = mock(EventMetadataWarmUpTask.class);
        ElasticsearchWarmUpTask elasticsearchTask = mock(ElasticsearchWarmUpTask.class);
        InfraWarmUpRunner runner = new InfraWarmUpRunner(
                slotTask,
                budgetTask,
                eventMetadataTask,
                elasticsearchTask);

        runner.run(mock(ApplicationArguments.class));

        InOrder order = inOrder(slotTask, budgetTask, eventMetadataTask, elasticsearchTask);
        order.verify(slotTask).warmUp();
        order.verify(budgetTask).warmUp();
        order.verify(eventMetadataTask).warmUp();
        order.verify(elasticsearchTask).warmUp();
    }

    @Test
    void shouldContinueWhenOneWarmUpTaskFails() throws Exception {
        SlotWarmUpTask slotTask = mock(SlotWarmUpTask.class);
        BudgetWarmUpTask budgetTask = mock(BudgetWarmUpTask.class);
        EventMetadataWarmUpTask eventMetadataTask = mock(EventMetadataWarmUpTask.class);
        ElasticsearchWarmUpTask elasticsearchTask = mock(ElasticsearchWarmUpTask.class);
        InfraWarmUpRunner runner = new InfraWarmUpRunner(
                slotTask,
                budgetTask,
                eventMetadataTask,
                elasticsearchTask);
        doThrow(new IllegalStateException("test failure")).when(budgetTask).warmUp();

        runner.run(mock(ApplicationArguments.class));

        verify(slotTask).warmUp();
        verify(budgetTask).warmUp();
        verify(eventMetadataTask).warmUp();
        verify(elasticsearchTask).warmUp();
    }
}
