package com.codity.jobscheduler.service;

import com.codity.jobscheduler.dto.JobDto;
import com.codity.jobscheduler.entity.*;
import com.codity.jobscheduler.enums.JobStatus;
import com.codity.jobscheduler.enums.LogLevel;
import com.codity.jobscheduler.exception.*;
import com.codity.jobscheduler.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepository jobRepository;
    private final QueueRepository queueRepository;
    private final RetryPolicyRepository retryPolicyRepository;
    private final JobLogRepository jobLogRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final ScheduledJobRepository scheduledJobRepository;
    private final JobStateMachine stateMachine;

    /**
     * Creates an immediate or delayed job. If scheduledAt is provided, the job
     * starts in SCHEDULED state; otherwise QUEUED.
     *
     * Idempotency: If an idempotencyKey is provided, checks for existing job
     * in the same queue with that key. Returns existing job instead of creating
     * a duplicate — this is the idempotent submission guarantee.
     */
    @Transactional
    public Job createJob(Long queueId, JobDto.CreateJobRequest request) {
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue", queueId));

        // Idempotency check: if key exists, return existing job (no duplicate)
        if (request.getIdempotencyKey() != null) {
            var existing = jobRepository.findByQueueIdAndIdempotencyKey(queueId, request.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotent hit: job already exists with key={} in queue={}",
                        request.getIdempotencyKey(), queueId);
                return existing.get();
            }
        }

        // Determine initial status based on scheduledAt
        JobStatus initialStatus = JobStatus.QUEUED;
        Instant scheduledAt = null;
        if (request.getScheduledAt() != null) {
            scheduledAt = Instant.parse(request.getScheduledAt());
            initialStatus = JobStatus.SCHEDULED;
        }

        // Snapshot max_retries from queue's retry policy at creation time
        int maxRetries = 3;
        if (queue.getRetryPolicy() != null) {
            maxRetries = queue.getRetryPolicy().getMaxRetries();
        }

        Job job = Job.builder()
                .queue(queue)
                .type(request.getType())
                .payload(request.getPayload())
                .idempotencyKey(request.getIdempotencyKey())
                .status(initialStatus)
                .priority(request.getPriority() != null ? request.getPriority() : 0)
                .attemptCount(0)
                .maxRetries(maxRetries)
                .scheduledAt(scheduledAt)
                .build();

        job = jobRepository.save(job);

        // Log job creation
        logJobEvent(job, null, LogLevel.INFO,
                "Job created with status " + initialStatus + ", type=" + request.getType());

        return job;
    }

    /**
     * Creates multiple jobs in a single transaction (batch submission).
     */
    @Transactional
    public List<Job> createBatchJobs(Long queueId, JobDto.CreateBatchJobRequest request) {
        List<Job> jobs = new ArrayList<>();
        for (JobDto.CreateJobRequest jobReq : request.getJobs()) {
            jobs.add(createJob(queueId, jobReq));
        }
        return jobs;
    }

    /**
     * Creates a recurring scheduled job (cron-based).
     * The scheduler service will fire these according to the cron expression.
     */
    @Transactional
    public ScheduledJob createScheduledJob(Long queueId, JobDto.CreateScheduledJobRequest request) {
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue", queueId));

        ScheduledJob scheduledJob = ScheduledJob.builder()
                .queue(queue)
                .jobType(request.getJobType())
                .payload(request.getPayload())
                .cronExpression(request.getCronExpression())
                .timezone(request.getTimezone() != null ? request.getTimezone() : "UTC")
                .isActive(true)
                .build();

        // TODO: compute next_fire_at from cron expression
        scheduledJob.setNextFireAt(Instant.now().plusSeconds(60));

        return scheduledJobRepository.save(scheduledJob);
    }

    /**
     * Transitions a job to a new status, enforcing the state machine.
     */
    @Transactional
    public Job transitionStatus(Long jobId, JobStatus newStatus) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", jobId));

        stateMachine.validateTransition(job.getStatus(), newStatus);
        JobStatus oldStatus = job.getStatus();
        job.setStatus(newStatus);

        if (newStatus == JobStatus.COMPLETED || newStatus == JobStatus.FAILED || newStatus == JobStatus.DEAD) {
            job.setCompletedAt(Instant.now());
        }

        job = jobRepository.save(job);
        logJobEvent(job, null, LogLevel.INFO,
                "Status transition: " + oldStatus + " → " + newStatus);
        return job;
    }

    /**
     * Moves a failed job to the dead letter queue.
     */
    @Transactional
    public void moveToDeadLetterQueue(Job job, String failureReason, String lastError) {
        stateMachine.validateTransition(job.getStatus(), JobStatus.DEAD);
        job.setStatus(JobStatus.DEAD);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);

        DeadLetterEntry dlqEntry = DeadLetterEntry.builder()
                .job(job)
                .queue(job.getQueue())
                .failureReason(failureReason)
                .lastError(lastError)
                .totalAttempts(job.getAttemptCount())
                .build();

        deadLetterRepository.save(dlqEntry);
        logJobEvent(job, null, LogLevel.ERROR, "Moved to DLQ: " + failureReason);
    }

    /**
     * Requeues a dead letter job — manual recovery by admin.
     */
    @Transactional
    public Job requeueFromDlq(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", jobId));

        stateMachine.validateTransition(job.getStatus(), JobStatus.QUEUED);
        job.setStatus(JobStatus.QUEUED);
        job.setAttemptCount(0);
        job.setCompletedAt(null);
        job.setStartedAt(null);
        jobRepository.save(job);

        // Update DLQ entry with requeue timestamp
        deadLetterRepository.findByJobId(jobId).ifPresent(dlq -> {
            dlq.setRequeuedAt(Instant.now());
            deadLetterRepository.save(dlq);
        });

        logJobEvent(job, null, LogLevel.INFO, "Requeued from DLQ by admin");
        return job;
    }

    public Page<Job> getJobsByQueue(Long queueId, JobStatus status, Pageable pageable) {
        if (status != null) {
            return jobRepository.findByQueueIdAndStatus(queueId, status, pageable);
        }
        return jobRepository.findByQueueId(queueId, pageable);
    }

    public Job getJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", jobId));
    }

    /**
     * Builds queue statistics — job counts by status.
     */
    public JobDto.QueueStatsResponse getQueueStats(Long queueId) {
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue", queueId));

        JobDto.QueueStatsResponse stats = new JobDto.QueueStatsResponse();
        stats.setQueueId(queue.getId());
        stats.setQueueName(queue.getName());
        stats.setIsPaused(queue.getIsPaused());
        stats.setConcurrencyLimit(queue.getConcurrencyLimit());

        List<Object[]> counts = jobRepository.countByStatusForQueue(queueId);
        long total = 0;
        for (Object[] row : counts) {
            JobStatus s = (JobStatus) row[0];
            long count = (Long) row[1];
            total += count;
            switch (s) {
                case QUEUED, SCHEDULED, CLAIMED -> stats.setQueued(stats.getQueued() + count);
                case RUNNING -> stats.setRunning(count);
                case COMPLETED -> stats.setCompleted(count);
                case FAILED -> stats.setFailed(count);
                case DEAD -> stats.setDead(count);
                default -> {}
            }
        }
        stats.setTotal(total);
        return stats;
    }

    private void logJobEvent(Job job, JobExecution execution, LogLevel level, String message) {
        JobLog jobLog = JobLog.builder()
                .job(job)
                .execution(execution)
                .level(level)
                .message(message)
                .build();
        jobLogRepository.save(jobLog);
    }
}
