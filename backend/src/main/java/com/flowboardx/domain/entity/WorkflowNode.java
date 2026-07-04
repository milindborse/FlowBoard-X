package com.flowboardx.domain.entity;

import com.flowboardx.domain.enums.NodeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "workflow_nodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowNode {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_version_id", nullable = false)
    private WorkflowVersion workflowVersion;

    /** Stable client-generated id used inside the React Flow graph (e.g. "node_3"). */
    @Column(nullable = false)
    private String clientNodeId;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeType type;

    @Column(nullable = false)
    private double positionX;

    @Column(nullable = false)
    private double positionY;

    /** Node-specific configuration (URL/method for HTTP, query for Postgres, etc.) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String configJson;

    @Column
    private Integer retryMaxAttempts;

    @Column
    private Long retryBaseBackoffMs;
}
