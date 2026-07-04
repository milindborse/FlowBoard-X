package com.flowboardx.repository;

import com.flowboardx.domain.entity.NodeExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NodeExecutionRepository extends JpaRepository<NodeExecution, UUID> {
    List<NodeExecution> findByWorkflowRun_IdOrderByStartedAtAsc(UUID workflowRunId);

    List<NodeExecution> findByWorkflowRun_IdAndClientNodeId(UUID workflowRunId, String clientNodeId);
}
