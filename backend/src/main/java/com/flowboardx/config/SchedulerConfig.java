package com.flowboardx.config;

import com.flowboardx.domain.entity.Workflow;
import com.flowboardx.dto.TriggerRunRequest;
import com.flowboardx.domain.enums.TriggerType;
import com.flowboardx.repository.WorkflowRepository;
import com.flowboardx.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SchedulerConfig {

    private final WorkflowRepository workflowRepo;
    private final ExecutionService executionService;

    /**
     * Polls every 60 s and compares each scheduled workflow's cron expression
     * against the current minute. This is a straightforward polling scheduler
     * suitable for a single-instance deployment; for multi-instance, the cron
     * trigger should be moved behind a distributed lock (Redis SET NX).
     */
    @Scheduled(fixedDelay = 60_000)
    public void triggerScheduledWorkflows() {
        List<Workflow> scheduled = workflowRepo.findAllScheduled();
        LocalDateTime now = LocalDateTime.now();
        for (Workflow workflow : scheduled) {
            try {
                CronExpression cron = CronExpression.parse(workflow.getCronExpression());
                // Fire if cron next-time falls within current minute window
                if (cron.next(now.minusMinutes(1)) != null &&
                        !cron.next(now.minusMinutes(1)).isAfter(now)) {
                    TriggerRunRequest req = new TriggerRunRequest();
                    req.setTriggerType(TriggerType.SCHEDULED);
                    executionService.triggerRun(workflow.getId(), req, null);
                    log.info("Scheduled trigger fired for workflow {}", workflow.getName());
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate cron for workflow {}: {}", workflow.getName(), e.getMessage());
            }
        }
    }
}
