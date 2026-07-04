package com.flowboardx.domain.entity;

import com.flowboardx.domain.enums.WorkflowStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Workflow {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    /** Free-text grouping label, e.g. "Finance", "Marketing". Optional. */
    @Column(length = 100)
    private String category;

    /** Lifecycle status: DRAFT until the first version is published. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WorkflowStatus status = WorkflowStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean isTemplate = false;

    @Column
    private Integer currentVersionNumber;

    @Column
    private String cronExpression;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (currentVersionNumber == null) currentVersionNumber = 0;
        if (status == null) status = WorkflowStatus.DRAFT;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}