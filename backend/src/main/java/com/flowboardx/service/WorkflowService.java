package com.flowboardx.service;

import com.flowboardx.domain.entity.*;
import com.flowboardx.domain.enums.AuditAction;
import com.flowboardx.domain.enums.WorkflowStatus;
import com.flowboardx.dto.*;
import com.flowboardx.exception.DuplicateResourceException;
import com.flowboardx.exception.ForbiddenOperationException;
import com.flowboardx.exception.ResourceNotFoundException;
import com.flowboardx.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepo;
    private final WorkflowVersionRepository versionRepo;
    private final WorkflowNodeRepository nodeRepo;
    private final WorkflowEdgeRepository edgeRepo;
    private final AuditLogRepository auditRepo;

    // ── Reads ──────────────────────────────────────────────────────────────────

    /**
     * Filtered, paginated, owner-scoped listing.
     * @param name partial, case-insensitive name filter (nullable)
     * @param status workflow lifecycle status filter (nullable)
     * @param versionNumber exact current-version filter (nullable)
     */
    public Page<WorkflowResponse> listWorkflows(String name, WorkflowStatus status, Integer versionNumber,
                                                 Pageable pageable, User actor) {
        Page<Workflow> page = workflowRepo.findByOwner_IdAndActiveTrue(actor.getId(), pageable);

        String nameFilter = blankToNull(name);
        List<Workflow> filtered = page.getContent().stream()
                .filter(w -> nameFilter == null || w.getName().toLowerCase().contains(nameFilter.toLowerCase()))
                .filter(w -> status == null || w.getStatus() == status)
                .filter(w -> versionNumber == null || versionNumber.equals(w.getCurrentVersionNumber()))
                .collect(Collectors.toList());

        log.info("Listed workflows userId={} totalOwned={} afterFilter={}", actor.getId(), page.getTotalElements(), filtered.size());

        List<WorkflowResponse> content = filtered.stream().map(this::toResponse).collect(Collectors.toList());
        return new org.springframework.data.domain.PageImpl<>(content, pageable, filtered.size());
    }

    public WorkflowResponse getWorkflow(UUID id, User actor) {
        Workflow workflow = findOrThrow(id);
        assertOwnership(workflow, actor);
        return toResponse(workflow);
    }

    public List<WorkflowResponse> getTemplates() {
        return workflowRepo.findByIsTemplateTrue().stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Create ─────────────────────────────────────────────────────────────────

    /**
     * Step 1 of the create flow: save metadata only (name/description/category).
     * The builder opens client-side only after this returns 201.
     */
    @Transactional
    public WorkflowResponse createWorkflow(CreateWorkflowRequest request, User actor) {
        String trimmedName = request.getName().trim();
        assertNameAvailable(trimmedName, actor.getId(), null);

        Workflow workflow = Workflow.builder()
                .name(trimmedName)
                .description(request.getDescription())
                .category(request.getCategory())
                .status(WorkflowStatus.DRAFT)
                .owner(actor)
                .build();
        workflow = workflowRepo.save(workflow);

        audit(actor, workflow, null, AuditAction.WORKFLOW_CREATED, "Created workflow '" + workflow.getName() + "'");
        log.info("Workflow created workflowId={} userId={} name={}", workflow.getId(), actor.getId(), workflow.getName());
        return toResponse(workflow);
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    @Transactional
    public WorkflowResponse updateWorkflow(UUID id, WorkflowRequest request, User actor) {
        Workflow workflow = findOrThrow(id);
        assertOwnership(workflow, actor);

        String trimmedName = request.getName().trim();
        if (!trimmedName.equalsIgnoreCase(workflow.getName())) {
            assertNameAvailable(trimmedName, actor.getId(), workflow.getId());
        }

        workflow.setName(trimmedName);
        workflow.setDescription(request.getDescription());
        workflow.setCategory(request.getCategory());
        workflow.setCronExpression(request.getCronExpression());
        workflow = workflowRepo.save(workflow);

        audit(actor, workflow, null, AuditAction.WORKFLOW_UPDATED, "Updated workflow metadata");
        log.info("Workflow updated workflowId={} userId={}", workflow.getId(), actor.getId());
        return toResponse(workflow);
    }

    /** PATCH /api/workflows/{id}/publish — flips lifecycle status to PUBLISHED. */
    @Transactional
    public WorkflowResponse publishWorkflow(UUID id, User actor) {
        Workflow workflow = findOrThrow(id);
        assertOwnership(workflow, actor);
        workflow.setStatus(WorkflowStatus.PUBLISHED);
        workflow = workflowRepo.save(workflow);
        audit(actor, workflow, null, AuditAction.WORKFLOW_PUBLISHED, "Workflow marked published");
        log.info("Workflow published workflowId={} userId={}", workflow.getId(), actor.getId());
        return toResponse(workflow);
    }

    @Transactional
    public void deleteWorkflow(UUID id, User actor) {
        Workflow workflow = findOrThrow(id);
        assertOwnership(workflow, actor);
        workflow.setActive(false);
        workflowRepo.save(workflow);
        audit(actor, workflow, null, AuditAction.WORKFLOW_DELETED, "Soft-deleted workflow");
        log.info("Workflow deleted workflowId={} userId={}", workflow.getId(), actor.getId());
    }

    // ── Versions ───────────────────────────────────────────────────────────────

    @Transactional
    public WorkflowVersionResponse saveVersion(UUID workflowId, SaveVersionRequest request, User actor) {
        Workflow workflow = findOrThrow(workflowId);
        assertOwnership(workflow, actor);

        int nextVersion = workflow.getCurrentVersionNumber() + 1;

        WorkflowVersion version = WorkflowVersion.builder()
                .workflow(workflow)
                .versionNumber(nextVersion)
                .published(request.isPublish())
                .changeSummary(request.getChangeSummary())
                .build();
        version = versionRepo.save(version);

        final WorkflowVersion savedVersion = version;

        List<WorkflowNode> nodes = request.getNodes().stream().map(dto -> WorkflowNode.builder()
                .workflowVersion(savedVersion)
                .clientNodeId(dto.getClientNodeId())
                .label(dto.getLabel())
                .type(dto.getType())
                .positionX(dto.getPositionX())
                .positionY(dto.getPositionY())
                .configJson(dto.getConfigJson())
                .retryMaxAttempts(dto.getRetryMaxAttempts())
                .retryBaseBackoffMs(dto.getRetryBaseBackoffMs())
                .build()).collect(Collectors.toList());
        nodeRepo.saveAll(nodes);

        List<WorkflowEdge> edges = request.getEdges().stream().map(dto -> WorkflowEdge.builder()
                .workflowVersion(savedVersion)
                .sourceClientNodeId(dto.getSourceClientNodeId())
                .targetClientNodeId(dto.getTargetClientNodeId())
                .branchLabel(dto.getBranchLabel())
                .build()).collect(Collectors.toList());
        edgeRepo.saveAll(edges);

        workflow.setCurrentVersionNumber(nextVersion);
        if (request.isPublish()) {
            workflow.setStatus(WorkflowStatus.PUBLISHED);
        }
        workflowRepo.save(workflow);

        if (request.isPublish()) {
            audit(actor, workflow, null, AuditAction.WORKFLOW_PUBLISHED, "Published v" + nextVersion);
        } else {
            audit(actor, workflow, null, AuditAction.WORKFLOW_UPDATED, "Saved draft v" + nextVersion);
        }
        log.info("Workflow version saved workflowId={} userId={} version={} published={}",
                workflow.getId(), actor.getId(), nextVersion, request.isPublish());

        return toVersionResponse(version, request.getNodes(), request.getEdges());
    }

    public WorkflowVersionResponse getVersion(UUID workflowId, Integer versionNumber, User actor) {
        Workflow workflow = findOrThrow(workflowId);
        assertOwnership(workflow, actor);

        WorkflowVersion version = (versionNumber != null)
                ? versionRepo.findByWorkflow_IdAndVersionNumber(workflowId, versionNumber)
                        .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + versionNumber))
                : versionRepo.findTopByWorkflow_IdOrderByVersionNumberDesc(workflowId)
                        .orElseThrow(() -> new ResourceNotFoundException("No versions saved yet for this workflow"));

        List<WorkflowNode> nodes = nodeRepo.findByWorkflowVersion_Id(version.getId());
        List<WorkflowEdge> edges = edgeRepo.findByWorkflowVersion_Id(version.getId());

        return toVersionResponse(version,
                nodes.stream().map(this::toNodeDto).collect(Collectors.toList()),
                edges.stream().map(this::toEdgeDto).collect(Collectors.toList()));
    }

    // ── Ownership / validation helpers ───────────────────────────────────────────

    private void assertOwnership(Workflow workflow, User actor) {
        if (workflow.getOwner() == null || !workflow.getOwner().getId().equals(actor.getId())) {
            log.warn("Forbidden access attempt workflowId={} userId={}", workflow.getId(), actor.getId());
            throw new ForbiddenOperationException("You do not have access to this workflow");
        }
    }

    private void assertNameAvailable(String name, UUID ownerId, UUID excludeWorkflowId) {
        boolean exists = (excludeWorkflowId == null)
                ? workflowRepo.existsByNameIgnoreCaseAndOwner_IdAndActiveTrue(name, ownerId)
                : workflowRepo.existsByNameIgnoreCaseAndOwner_IdAndActiveTrueAndIdNot(name, ownerId, excludeWorkflowId);
        if (exists) {
            throw new DuplicateResourceException("A workflow named '" + name + "' already exists");
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    // --- mapping helpers ---

    private WorkflowResponse toResponse(Workflow w) {
        return WorkflowResponse.builder()
                .id(w.getId())
                .name(w.getName())
                .description(w.getDescription())
                .category(w.getCategory())
                .status(w.getStatus())
                .ownerId(w.getOwner() != null ? w.getOwner().getId() : null)
                .active(w.isActive())
                .isTemplate(w.isTemplate())
                .currentVersionNumber(w.getCurrentVersionNumber())
                .cronExpression(w.getCronExpression())
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .build();
    }

    private WorkflowVersionResponse toVersionResponse(WorkflowVersion v, List<NodeDto> nodes, List<EdgeDto> edges) {
        return WorkflowVersionResponse.builder()
                .id(v.getId())
                .versionNumber(v.getVersionNumber())
                .published(v.isPublished())
                .changeSummary(v.getChangeSummary())
                .nodes(nodes)
                .edges(edges)
                .createdAt(v.getCreatedAt())
                .build();
    }

    private NodeDto toNodeDto(WorkflowNode n) {
        NodeDto dto = new NodeDto();
        dto.setClientNodeId(n.getClientNodeId());
        dto.setLabel(n.getLabel());
        dto.setType(n.getType());
        dto.setPositionX(n.getPositionX());
        dto.setPositionY(n.getPositionY());
        dto.setConfigJson(n.getConfigJson());
        dto.setRetryMaxAttempts(n.getRetryMaxAttempts());
        dto.setRetryBaseBackoffMs(n.getRetryBaseBackoffMs());
        return dto;
    }

    private EdgeDto toEdgeDto(WorkflowEdge e) {
        EdgeDto dto = new EdgeDto();
        dto.setSourceClientNodeId(e.getSourceClientNodeId());
        dto.setTargetClientNodeId(e.getTargetClientNodeId());
        dto.setBranchLabel(e.getBranchLabel());
        return dto;
    }

    private Workflow findOrThrow(UUID id) {
        return workflowRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + id));
    }

    private void audit(User actor, Workflow workflow, UUID runId, AuditAction action, String details) {
        auditRepo.save(AuditLog.builder()
                .actor(actor)
                .workflow(workflow)
                .workflowRunId(runId)
                .action(action)
                .details(details)
                .build());
    }
}