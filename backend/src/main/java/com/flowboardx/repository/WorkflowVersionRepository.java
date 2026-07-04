package com.flowboardx.repository;

import com.flowboardx.domain.entity.WorkflowVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, UUID> {
    List<WorkflowVersion> findByWorkflow_IdOrderByVersionNumberDesc(UUID workflowId);

    Optional<WorkflowVersion> findByWorkflow_IdAndVersionNumber(UUID workflowId, Integer versionNumber);

    Optional<WorkflowVersion> findTopByWorkflow_IdOrderByVersionNumberDesc(UUID workflowId);

    Optional<WorkflowVersion> findTopByWorkflow_IdAndPublishedTrueOrderByVersionNumberDesc(UUID workflowId);
}
