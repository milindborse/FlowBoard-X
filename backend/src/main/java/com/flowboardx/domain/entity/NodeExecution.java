package com.flowboardx.domain.entity;

import com.flowboardx.domain.enums.NodeExecutionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "node_executions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeExecution {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_run_id", nullable = false)
    private WorkflowRun workflowRun;

    @Column(nullable = false)
    private String clientNodeId;

    @Column(nullable = false)
    private String nodeLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeExecutionStatus status;

    @Column(nullable = false)
    @Builder.Default
    private int attemptNumber = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String inputJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String outputJson;

    @Column(length = 4000)
    private String logOutput;

    @Column
    private String errorMessage;

    @Column
    private Instant startedAt;

    @Column
    private Instant finishedAt;

    @Column
    private Long durationMs;
}
