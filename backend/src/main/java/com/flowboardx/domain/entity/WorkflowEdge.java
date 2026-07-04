package com.flowboardx.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "workflow_edges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowEdge {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_version_id", nullable = false)
    private WorkflowVersion workflowVersion;

    @Column(nullable = false)
    private String sourceClientNodeId;

    @Column(nullable = false)
    private String targetClientNodeId;

    /** For Condition nodes: "true" / "false" branch label, otherwise null. */
    @Column
    private String branchLabel;
}
