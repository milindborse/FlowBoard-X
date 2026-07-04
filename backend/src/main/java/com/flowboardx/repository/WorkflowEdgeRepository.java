package com.flowboardx.repository;

import com.flowboardx.domain.entity.WorkflowEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkflowEdgeRepository extends JpaRepository<WorkflowEdge, UUID> {
    List<WorkflowEdge> findByWorkflowVersion_Id(UUID workflowVersionId);

    void deleteByWorkflowVersion_Id(UUID workflowVersionId);
}
