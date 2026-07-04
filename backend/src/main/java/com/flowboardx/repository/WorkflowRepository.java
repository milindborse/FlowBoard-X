package com.flowboardx.repository;

import com.flowboardx.domain.entity.Workflow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    Page<Workflow> findByActiveTrue(Pageable pageable);

    Page<Workflow> findByNameContainingIgnoreCaseAndActiveTrue(String name, Pageable pageable);

    List<Workflow> findByIsTemplateTrue();

    @Query("select w from Workflow w where w.cronExpression is not null and w.active = true")
    List<Workflow> findAllScheduled();

    /** Uniqueness check: workflow names must be unique per owner (case-insensitive), among active workflows. */
    boolean existsByNameIgnoreCaseAndOwner_IdAndActiveTrue(String name, UUID ownerId);

    /** Same check but excluding the workflow being updated (so renaming to your own current name is allowed). */
    boolean existsByNameIgnoreCaseAndOwner_IdAndActiveTrueAndIdNot(String name, UUID ownerId, UUID excludeId);

    /**
     * Owner-scoped, paginated base listing (sorted per the Pageable's Sort).
     * Name/status/version filtering is applied afterward in WorkflowService, in-memory —
     * deliberately kept simple here to avoid multi-condition JPQL edge cases with Spring
     * Data's auto-generated count query, which can silently return empty pages.
     */
    Page<Workflow> findByOwner_IdAndActiveTrue(UUID ownerId, Pageable pageable);
}