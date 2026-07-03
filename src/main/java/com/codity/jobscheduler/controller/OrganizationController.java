package com.codity.jobscheduler.controller;

import com.codity.jobscheduler.dto.JobDto;
import com.codity.jobscheduler.entity.*;
import com.codity.jobscheduler.enums.OrgRole;
import com.codity.jobscheduler.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Organizations & Projects", description = "Org, project, queue, and retry policy management")
public class OrganizationController {

    private final OrganizationService orgService;

    // --- Organizations ---

    @PostMapping("/organizations")
    @Operation(summary = "Create a new organization")
    public ResponseEntity<Organization> createOrg(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody JobDto.CreateOrgRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orgService.createOrganization(user, request));
    }

    @GetMapping("/organizations/{orgId}")
    @Operation(summary = "Get organization details")
    public ResponseEntity<Organization> getOrg(
            @AuthenticationPrincipal User user,
            @PathVariable Long orgId) {
        orgService.requireRole(user.getId(), orgId, OrgRole.MEMBER);
        return ResponseEntity.ok(orgService.getOrganization(orgId));
    }

    // --- Projects ---

    @PostMapping("/organizations/{orgId}/projects")
    @Operation(summary = "Create a project within an organization")
    public ResponseEntity<Project> createProject(
            @AuthenticationPrincipal User user,
            @PathVariable Long orgId,
            @Valid @RequestBody JobDto.CreateProjectRequest request) {
        orgService.requireRole(user.getId(), orgId, OrgRole.ADMIN);
        return ResponseEntity.status(HttpStatus.CREATED).body(orgService.createProject(orgId, request));
    }

    @GetMapping("/organizations/{orgId}/projects")
    @Operation(summary = "List projects in an organization")
    public ResponseEntity<Page<Project>> getProjects(
            @AuthenticationPrincipal User user,
            @PathVariable Long orgId,
            Pageable pageable) {
        orgService.requireRole(user.getId(), orgId, OrgRole.MEMBER);
        return ResponseEntity.ok(orgService.getProjects(orgId, pageable));
    }

    // --- Queues ---

    @PostMapping("/projects/{projectId}/queues")
    @Operation(summary = "Create a queue within a project")
    public ResponseEntity<Queue> createQueue(
            @PathVariable Long projectId,
            @Valid @RequestBody JobDto.CreateQueueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orgService.createQueue(projectId, request));
    }

    @PatchMapping("/queues/{queueId}")
    @Operation(summary = "Update queue configuration (priority, concurrency, pause/resume)")
    public ResponseEntity<Queue> updateQueue(
            @PathVariable Long queueId,
            @Valid @RequestBody JobDto.UpdateQueueRequest request) {
        return ResponseEntity.ok(orgService.updateQueue(queueId, request));
    }

    @PatchMapping("/queues/{queueId}/pause")
    @Operation(summary = "Pause a queue")
    public ResponseEntity<Queue> pauseQueue(@PathVariable Long queueId) {
        JobDto.UpdateQueueRequest req = new JobDto.UpdateQueueRequest();
        req.setIsPaused(true);
        return ResponseEntity.ok(orgService.updateQueue(queueId, req));
    }

    @PatchMapping("/queues/{queueId}/resume")
    @Operation(summary = "Resume a paused queue")
    public ResponseEntity<Queue> resumeQueue(@PathVariable Long queueId) {
        JobDto.UpdateQueueRequest req = new JobDto.UpdateQueueRequest();
        req.setIsPaused(false);
        return ResponseEntity.ok(orgService.updateQueue(queueId, req));
    }

    @GetMapping("/projects/{projectId}/queues")
    @Operation(summary = "List queues in a project")
    public ResponseEntity<Page<Queue>> getQueues(
            @PathVariable Long projectId,
            Pageable pageable) {
        return ResponseEntity.ok(orgService.getQueues(projectId, pageable));
    }

    // --- Retry Policies ---

    @PostMapping("/retry-policies")
    @Operation(summary = "Create a retry policy")
    public ResponseEntity<RetryPolicy> createRetryPolicy(
            @Valid @RequestBody JobDto.CreateRetryPolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orgService.createRetryPolicy(request));
    }
}
