package com.codity.jobscheduler.service;

import com.codity.jobscheduler.enums.JobStatus;
import com.codity.jobscheduler.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Enforces the job lifecycle state machine. Every status transition must go through
 * this component — direct status updates bypassing validation are a bug.
 *
 * State Machine:
 *   [*] → QUEUED (immediate job)
 *   [*] → SCHEDULED (delayed/cron job)
 *   SCHEDULED → QUEUED (scheduled_at reached)
 *   QUEUED → CLAIMED (worker locks row via SKIP LOCKED)
 *   CLAIMED → RUNNING (worker begins execution)
 *   RUNNING → COMPLETED (success)
 *   RUNNING → RETRYING (error, retries remaining)
 *   RUNNING → FAILED (error, retries exhausted)
 *   RETRYING → QUEUED (after backoff delay)
 *   FAILED → DEAD (moved to DLQ)
 *   DEAD → QUEUED (manual requeue by admin)
 */
@Component
public class JobStateMachine {

    /**
     * Map of valid transitions: from-status → set of allowed to-statuses.
     */
    private static final Map<JobStatus, Set<JobStatus>> VALID_TRANSITIONS = Map.of(
            JobStatus.QUEUED, Set.of(JobStatus.CLAIMED),
            JobStatus.SCHEDULED, Set.of(JobStatus.QUEUED),
            JobStatus.CLAIMED, Set.of(JobStatus.RUNNING),
            JobStatus.RUNNING, Set.of(JobStatus.COMPLETED, JobStatus.RETRYING, JobStatus.FAILED),
            JobStatus.RETRYING, Set.of(JobStatus.QUEUED),
            JobStatus.FAILED, Set.of(JobStatus.DEAD),
            JobStatus.DEAD, Set.of(JobStatus.QUEUED)
    );

    /**
     * Validates that the transition from → to is allowed by the state machine.
     * Throws InvalidStateTransitionException if not.
     *
     * @param from Current status
     * @param to   Target status
     * @throws InvalidStateTransitionException if transition is invalid
     */
    public void validateTransition(JobStatus from, JobStatus to) {
        Set<JobStatus> allowed = VALID_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new InvalidStateTransitionException(from, to);
        }
    }

    /**
     * Returns true if the given transition is valid without throwing.
     */
    public boolean isValidTransition(JobStatus from, JobStatus to) {
        Set<JobStatus> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Returns the set of statuses reachable from the given status.
     */
    public Set<JobStatus> getValidNextStatuses(JobStatus from) {
        return VALID_TRANSITIONS.getOrDefault(from, Set.of());
    }
}
