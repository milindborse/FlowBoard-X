package com.flowboardx.repository;

import com.flowboardx.domain.entity.WorkflowNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkflowNodeRepository extends JpaRepository<WorkflowNode, UUID> {
    List<WorkflowNode> findByWorkflowVersion_Id(UUID workflowVersionId);

    void deleteByWorkflowVersion_Id(UUID workflowVersionId);
}
