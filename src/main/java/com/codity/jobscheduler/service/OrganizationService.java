package com.codity.jobscheduler.service;

import com.codity.jobscheduler.dto.JobDto;
import com.codity.jobscheduler.entity.*;
import com.codity.jobscheduler.enums.OrgRole;
import com.codity.jobscheduler.exception.*;
import com.codity.jobscheduler.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository orgRepository;
    private final OrganizationMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final QueueRepository queueRepository;
    private final RetryPolicyRepository retryPolicyRepository;

    @Transactional
    public Organization createOrganization(User user, JobDto.CreateOrgRequest request) {
        String slug = request.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        if (orgRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException("Organization slug already exists: " + slug);
        }

        Organization org = Organization.builder()
                .name(request.getName())
                .slug(slug)
                .build();
        org = orgRepository.save(org);

        // Creator becomes OWNER
        OrganizationMember membership = OrganizationMember.builder()
                .user(user)
                .organization(org)
                .role(OrgRole.OWNER)
                .build();
        memberRepository.save(membership);

        return org;
    }

    public Organization getOrganization(Long orgId) {
        return orgRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));
    }

    /**
     * Verifies user has at least the minimum required role in the organization.
     * Role hierarchy: OWNER > ADMIN > MEMBER
     */
    public void requireRole(Long userId, Long orgId, OrgRole minimumRole) {
        OrganizationMember member = memberRepository.findByUserIdAndOrganizationId(userId, orgId)
                .orElseThrow(() -> new UnauthorizedException("Not a member of this organization"));

        if (roleLevel(member.getRole()) < roleLevel(minimumRole)) {
            throw new UnauthorizedException(
                    "Requires at least " + minimumRole + " role, you have " + member.getRole());
        }
    }

    public boolean isMember(Long userId, Long orgId) {
        return memberRepository.existsByUserIdAndOrganizationId(userId, orgId);
    }

    private int roleLevel(OrgRole role) {
        return switch (role) {
            case OWNER -> 3;
            case ADMIN -> 2;
            case MEMBER -> 1;
        };
    }

    // --- Project CRUD ---

    @Transactional
    public Project createProject(Long orgId, JobDto.CreateProjectRequest request) {
        Organization org = getOrganization(orgId);
        if (projectRepository.existsByOrganizationIdAndName(orgId, request.getName())) {
            throw new DuplicateResourceException("Project already exists: " + request.getName());
        }
        Project project = Project.builder()
                .organization(org)
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return projectRepository.save(project);
    }

    public Page<Project> getProjects(Long orgId, Pageable pageable) {
        return projectRepository.findByOrganizationId(orgId, pageable);
    }

    public Project getProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    // --- Queue CRUD ---

    @Transactional
    public Queue createQueue(Long projectId, JobDto.CreateQueueRequest request) {
        Project project = getProject(projectId);
        if (queueRepository.existsByProjectIdAndName(projectId, request.getName())) {
            throw new DuplicateResourceException("Queue already exists: " + request.getName());
        }

        RetryPolicy retryPolicy = null;
        if (request.getRetryPolicyId() != null) {
            retryPolicy = retryPolicyRepository.findById(request.getRetryPolicyId())
                    .orElseThrow(() -> new ResourceNotFoundException("RetryPolicy", request.getRetryPolicyId()));
        }

        Queue queue = Queue.builder()
                .project(project)
                .name(request.getName())
                .priority(request.getPriority())
                .concurrencyLimit(request.getConcurrencyLimit())
                .isPaused(false)
                .retryPolicy(retryPolicy)
                .build();
        return queueRepository.save(queue);
    }

    @Transactional
    public Queue updateQueue(Long queueId, JobDto.UpdateQueueRequest request) {
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue", queueId));

        if (request.getPriority() != null) queue.setPriority(request.getPriority());
        if (request.getConcurrencyLimit() != null) queue.setConcurrencyLimit(request.getConcurrencyLimit());
        if (request.getIsPaused() != null) queue.setIsPaused(request.getIsPaused());
        if (request.getRetryPolicyId() != null) {
            RetryPolicy rp = retryPolicyRepository.findById(request.getRetryPolicyId())
                    .orElseThrow(() -> new ResourceNotFoundException("RetryPolicy", request.getRetryPolicyId()));
            queue.setRetryPolicy(rp);
        }
        return queueRepository.save(queue);
    }

    public Page<Queue> getQueues(Long projectId, Pageable pageable) {
        return queueRepository.findByProjectId(projectId, pageable);
    }

    // --- Retry Policy CRUD ---

    @Transactional
    public RetryPolicy createRetryPolicy(JobDto.CreateRetryPolicyRequest request) {
        RetryPolicy policy = RetryPolicy.builder()
                .name(request.getName())
                .strategy(request.getStrategy())
                .maxRetries(request.getMaxRetries())
                .initialDelayMs(request.getInitialDelayMs())
                .maxDelayMs(request.getMaxDelayMs())
                .multiplier(request.getMultiplier())
                .build();
        return retryPolicyRepository.save(policy);
    }
}
