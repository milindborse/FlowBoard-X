package com.flowboardx.repository;

import com.flowboardx.domain.entity.WorkflowRun;
import com.flowboardx.domain.enums.RunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, UUID> {
    Page<WorkflowRun> findByWorkflow_IdOrderByCreatedAtDesc(UUID workflowId, Pageable pageable);

    Page<WorkflowRun> findByStatusOrderByCreatedAtDesc(RunStatus status, Pageable pageable);

    List<WorkflowRun> findTop20ByOrderByCreatedAtDesc();

    long countByStatus(RunStatus status);

    long countByWorkflow_IdAndStatus(UUID workflowId, RunStatus status);

    List<WorkflowRun> findByCreatedAtAfter(Instant after);
}
