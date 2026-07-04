package com.flowboardx.service;

import com.flowboardx.domain.entity.WorkflowRun;
import com.flowboardx.domain.enums.RunStatus;
import com.flowboardx.dto.AnalyticsResponse;
import com.flowboardx.dto.DashboardStatsResponse;
import com.flowboardx.dto.RunResponse;
import com.flowboardx.repository.NodeExecutionRepository;
import com.flowboardx.repository.WorkflowRepository;
import com.flowboardx.repository.WorkflowRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final WorkflowRepository workflowRepo;
    private final WorkflowRunRepository runRepo;
    private final NodeExecutionRepository nodeExecRepo;

    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats() {
        long total = runRepo.count();
        long succeeded = runRepo.countByStatus(RunStatus.SUCCEEDED);
        long failed = runRepo.countByStatus(RunStatus.FAILED);
        long running = runRepo.countByStatus(RunStatus.RUNNING);

        double successRate = total > 0 ? (double) succeeded / total * 100 : 0;

        List<WorkflowRun> recent = runRepo.findTop20ByOrderByCreatedAtDesc();
        Long avgDuration = recent.stream()
                .filter(r -> r.getDurationMs() != null)
                .mapToLong(WorkflowRun::getDurationMs)
                .filter(d -> d > 0)
                .boxed()
                .reduce((a, b) -> (a + b) / 2)
                .orElse(null);

        List<RunResponse> recentDtos = recent.stream().map(r -> RunResponse.builder()
                .id(r.getId())
                .workflowId(r.getWorkflow().getId())
                .workflowName(r.getWorkflow().getName())
                .versionNumber(r.getWorkflowVersion().getVersionNumber())
                .status(r.getStatus())
                .triggerType(r.getTriggerType())
                .startedAt(r.getStartedAt())
                .finishedAt(r.getFinishedAt())
                .durationMs(r.getDurationMs())
                .createdAt(r.getCreatedAt())
                .build()).collect(Collectors.toList());

        return DashboardStatsResponse.builder()
                .totalWorkflows(workflowRepo.count())
                .totalRuns(total)
                .successfulRuns(succeeded)
                .failedRuns(failed)
                .runningRuns(running)
                .successRate(Math.round(successRate * 10.0) / 10.0)
                .avgDurationMs(avgDuration)
                .recentRuns(recentDtos)
                .build();
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics() {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        List<WorkflowRun> runs = runRepo.findByCreatedAtAfter(thirtyDaysAgo);

        // Runs per day
        Map<String, Long> runsPerDayMap = runs.stream().collect(
                Collectors.groupingBy(r -> r.getCreatedAt().toString().substring(0, 10), Collectors.counting()));
        List<Map<String, Object>> runsPerDay = runsPerDayMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> Map.<String, Object>of("date", e.getKey(), "runs", e.getValue()))
                .collect(Collectors.toList());

        // Success rate per day
        Map<String, Map<Boolean, Long>> groupedByDay = runs.stream().collect(
                Collectors.groupingBy(r -> r.getCreatedAt().toString().substring(0, 10),
                        Collectors.partitioningBy(r -> r.getStatus() == RunStatus.SUCCEEDED, Collectors.counting())));
        List<Map<String, Object>> successTrend = groupedByDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    long s = e.getValue().getOrDefault(true, 0L);
                    long f = e.getValue().getOrDefault(false, 0L);
                    double rate = (s + f) > 0 ? (double) s / (s + f) * 100 : 0;
                    return Map.<String, Object>of("date", e.getKey(), "successRate", Math.round(rate * 10.0) / 10.0);
                }).collect(Collectors.toList());

        // Avg duration per workflow
        Map<String, LongSummaryStatistics> durationMap = runs.stream()
                .filter(r -> r.getDurationMs() != null)
                .collect(Collectors.groupingBy(r -> r.getWorkflow().getName(),
                        Collectors.summarizingLong(WorkflowRun::getDurationMs)));
        List<Map<String, Object>> avgDurationPerWf = durationMap.entrySet().stream()
                .map(e -> Map.<String, Object>of("workflow", e.getKey(), "avgDurationMs", (long) e.getValue().getAverage()))
                .sorted(Comparator.comparingLong(m -> -(Long) m.get("avgDurationMs")))
                .limit(10)
                .collect(Collectors.toList());

        return AnalyticsResponse.builder()
                .runsPerDay(runsPerDay)
                .successRateTrend(successTrend)
                .avgDurationPerWorkflow(avgDurationPerWf)
                .nodeFailureFrequency(List.of())
                .build();
    }
}