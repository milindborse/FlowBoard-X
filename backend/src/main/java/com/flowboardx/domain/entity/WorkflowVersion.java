package com.flowboardx.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot of a workflow graph at a point in time.
 * Every save in the visual editor creates (or updates a draft) version.
 * Execution always binds to a specific version, which is what makes
 * replay-from-failure and rollback safe.
 */
@Entity
@Table(name = "workflow_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowVersion {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @Column(nullable = false)
    private Integer versionNumber;

    @Column(nullable = false)
    @Builder.Default
    private boolean published = false;

    /** Full graph snapshot (nodes + edges + positions) for fast editor reload. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String graphSnapshotJson;

    @Column
    private String changeSummary;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
