package com.codity.jobscheduler.worker;

import com.codity.jobscheduler.entity.*;
import com.codity.jobscheduler.enums.*;
import com.codity.jobscheduler.repository.*;
import com.codity.jobscheduler.service.JobService;
import com.codity.jobscheduler.service.JobStateMachine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Embedded worker service that polls queues, atomically claims jobs, and executes them.
 *
 * CONCURRENCY SAFETY:
 * - Uses SELECT ... FOR UPDATE SKIP LOCKED to prevent double-claiming
 * - Each worker instance tracks its own load via AtomicInteger
 * - Graceful shutdown: sets draining flag → finishes in-flight → releases
 *
 * RACE CONDITION PREVENTION:
 * 1. The database is the single source of truth for job ownership
 * 2. FOR UPDATE acquires an exclusive row lock — only one TX can hold it
 * 3. SKIP LOCKED ensures other workers don't block; they grab different rows
 * 4. The claim + status update happen in the same @Transactional method
 * 5. If a worker crashes mid-execution, the heartbeat monitor detects the
 *    stale heartbeat and can recover the stuck job
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "worker.enabled", havingValue = "true", matchIfMissing = true)
public class WorkerService {

    private final JobRepository jobRepository;
    private final JobExecutionRepository executionRepository;
    private final JobLogRepository jobLogRepository;
    private final QueueRepository queueRepository;
    private final WorkerRepository workerRepository;
    private final WorkerHeartbeatRepository heartbeatRepository;
    private final JobService jobService;
    private final JobStateMachine stateMachine;

    @Value("${worker.concurrency:5}")
    private int maxConcurrency;

    @Value("${worker.stale-heartbeat-threshold-ms:30000}")
    private long staleThresholdMs;

    private ExecutorService executorService;
    private Worker workerEntity;
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicInteger activeJobs = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        executorService = Executors.newFixedThreadPool(maxConcurrency);
        registerWorker();
        log.info("Worker initialized: name={}, concurrency={}", workerEntity.getWorkerName(), maxConcurrency);
    }

    /**
     * GRACEFUL SHUTDOWN:
     * 1. Set draining flag — poll loop stops claiming new jobs
     * 2. Mark worker as DRAINING in DB — other components see the state
     * 3. Wait for in-flight jobs to finish (up to 60s timeout)
     * 4. Mark worker as OFFLINE
     * 5. Shut down thread pool
     *
     * This prevents the worker from abandoning jobs mid-execution.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Graceful shutdown initiated for worker {}", workerEntity.getWorkerName());
        draining.set(true);

        // Mark as DRAINING so scheduler knows not to assign new work
        workerEntity.setStatus(WorkerStatus.DRAINING);
        workerRepository.save(workerEntity);

        // Wait for in-flight jobs to complete
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                log.warn("Forcing shutdown — {} jobs still running", activeJobs.get());
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Mark as OFFLINE
        workerEntity.setStatus(WorkerStatus.OFFLINE);
        workerEntity.setCurrentLoad(0);
        workerRepository.save(workerEntity);
        log.info("Worker {} shutdown complete", workerEntity.getWorkerName());
    }

    /**
     * POLL LOOP — Runs on a fixed schedule.
     * For each active queue (ordered by priority), attempts to claim one job
     * if the worker has capacity. The claim is atomic via SKIP LOCKED.
     *
     * Important: if draining is true, we skip entirely — no new claims.
     */
    @Scheduled(fixedDelayString = "${worker.poll-interval-ms:2000}")
    public void pollAndExecute() {
        if (draining.get()) return;

        List<Queue> activeQueues = queueRepository.findActiveQueuesOrderedByPriority();

        for (Queue queue : activeQueues) {
            // Check worker capacity
            if (activeJobs.get() >= maxConcurrency) break;

            // Check queue concurrency limit
            long runningInQueue = jobRepository.countByQueueIdAndStatus(
                    queue.getId(), JobStatus.RUNNING);
            if (runningInQueue >= queue.getConcurrencyLimit()) continue;

            // Attempt atomic claim
            try {
                claimAndExecute(queue.getId());
            } catch (Exception e) {
                log.error("Error polling queue {}: {}", queue.getName(), e.getMessage());
            }
        }
    }

    /**
     * ATOMIC JOB CLAIMING — The critical section.
     *
     * This method runs in a transaction. The sequence:
     * 1. SELECT ... FOR UPDATE SKIP LOCKED — locks one unclaimed row
     * 2. UPDATE status to CLAIMED — within the same transaction
     * 3. Create execution record — tracks this attempt
     * 4. COMMIT — releases the lock and persists changes atomically
     * 5. Submit execution to thread pool — runs outside the transaction
     *
     * WHY THIS IS SAFE:
     * - The SELECT + UPDATE happen atomically in one transaction
     * - SKIP LOCKED means concurrent workers don't block; they grab different jobs
     * - If this transaction fails/rolls back, the row is never claimed (stays QUEUED)
     * - The execution runs AFTER the transaction commits (no lock held during execution)
     */
    @Transactional
    public void claimAndExecute(Long queueId) {
        if (draining.get() || activeJobs.get() >= maxConcurrency) return;

        // Step 1 & 2: Atomic claim via SKIP LOCKED
        var optionalJob = jobRepository.claimNextJob(queueId, Instant.now());
        if (optionalJob.isEmpty()) return;

        Job job = optionalJob.get();

        // Step 2: Transition QUEUED → CLAIMED (state machine enforced)
        stateMachine.validateTransition(job.getStatus(), JobStatus.CLAIMED);
        job.setStatus(JobStatus.CLAIMED);
        job.setStartedAt(Instant.now());
        job.setAttemptCount(job.getAttemptCount() + 1);
        jobRepository.save(job);

        // Step 3: Create execution record
        JobExecution execution = JobExecution.builder()
                .job(job)
                .worker(workerEntity)
                .attemptNumber(job.getAttemptCount())
                .status(ExecutionStatus.RUNNING)
                .startedAt(Instant.now())
                .build();
        executionRepository.save(execution);

        // Update worker load (denormalized counter)
        activeJobs.incrementAndGet();
        workerEntity.setCurrentLoad(activeJobs.get());
        workerRepository.save(workerEntity);

        log.info("Claimed job {} (type={}, attempt={}) from queue {}",
                job.getId(), job.getType(), job.getAttemptCount(), queueId);

        // Step 5: Execute outside transaction — in thread pool
        executorService.submit(() -> executeJob(job, execution));
    }

    /**
     * Executes a job and handles success/failure transitions.
     * Runs in a separate thread, outside the claiming transaction.
     */
    private void executeJob(Job job, JobExecution execution) {
        Instant started = Instant.now();
        try {
            // Transition CLAIMED → RUNNING
            updateJobStatus(job.getId(), JobStatus.RUNNING);

            // --- SIMULATE JOB EXECUTION ---
            // In production, this would dispatch to a job handler registry
            // based on job.getType(). For this assessment, we simulate work.
            log.info("Executing job {} (type={})", job.getId(), job.getType());
            Thread.sleep(1000 + (long) (Math.random() * 2000)); // 1-3 seconds

            // Simulate 20% failure rate for demonstration
            if (Math.random() < 0.2) {
                throw new RuntimeException("Simulated job failure for testing");
            }

            // SUCCESS
            completeExecution(job, execution, started);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failExecution(job, execution, started, "Job interrupted during execution");
        } catch (Exception e) {
            failExecution(job, execution, started, e.getMessage());
        } finally {
            activeJobs.decrementAndGet();
            workerEntity.setCurrentLoad(activeJobs.get());
            workerRepository.save(workerEntity);
        }
    }

    @Transactional
    protected void completeExecution(Job job, JobExecution execution, Instant started) {
        long durationMs = Duration.between(started, Instant.now()).toMillis();

        execution.setStatus(ExecutionStatus.COMPLETED);
        execution.setFinishedAt(Instant.now());
        execution.setDurationMs(durationMs);
        executionRepository.save(execution);

        job = jobRepository.findById(job.getId()).orElseThrow();
        stateMachine.validateTransition(job.getStatus(), JobStatus.COMPLETED);
        job.setStatus(JobStatus.COMPLETED);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);

        log.info("Job {} completed in {}ms", job.getId(), durationMs);
    }

    /**
     * Handles job failure with retry logic.
     * If retries remain: RUNNING → RETRYING → (after delay) → QUEUED
     * If retries exhausted: RUNNING → FAILED → DEAD (moved to DLQ)
     */
    @Transactional
    protected void failExecution(Job job, JobExecution execution, Instant started, String error) {
        long durationMs = Duration.between(started, Instant.now()).toMillis();

        execution.setStatus(ExecutionStatus.FAILED);
        execution.setFinishedAt(Instant.now());
        execution.setDurationMs(durationMs);
        execution.setErrorMessage(error);
        executionRepository.save(execution);

        job = jobRepository.findById(job.getId()).orElseThrow();

        if (job.getAttemptCount() < job.getMaxRetries()) {
            // Retries remaining — transition to RETRYING then back to QUEUED
            stateMachine.validateTransition(job.getStatus(), JobStatus.RETRYING);
            job.setStatus(JobStatus.RETRYING);
            jobRepository.save(job);

            // Calculate backoff delay and set scheduledAt for delayed re-enqueue
            long delay = calculateRetryDelay(job);
            job.setStatus(JobStatus.QUEUED);
            job.setScheduledAt(Instant.now().plusMillis(delay));
            job.setStartedAt(null);
            jobRepository.save(job);

            log.info("Job {} failed (attempt {}), retrying in {}ms. Error: {}",
                    job.getId(), job.getAttemptCount(), delay, error);
        } else {
            // Retries exhausted — move to DLQ
            stateMachine.validateTransition(job.getStatus(), JobStatus.FAILED);
            job.setStatus(JobStatus.FAILED);
            jobRepository.save(job);

            jobService.moveToDeadLetterQueue(job,
                    "Max retries (" + job.getMaxRetries() + ") exhausted", error);

            log.warn("Job {} moved to DLQ after {} attempts. Last error: {}",
                    job.getId(), job.getAttemptCount(), error);
        }
    }

    /**
     * Calculates retry delay using the queue's retry policy.
     * Falls back to exponential backoff with defaults if no policy is configured.
     */
    private long calculateRetryDelay(Job job) {
        Queue queue = job.getQueue();
        if (queue.getRetryPolicy() != null) {
            return queue.getRetryPolicy().calculateDelay(job.getAttemptCount());
        }
        // Default: exponential backoff — 1s, 2s, 4s, 8s... capped at 5min
        long delay = (long) (1000 * Math.pow(2, job.getAttemptCount() - 1));
        return Math.min(delay, 300_000);
    }

    /**
     * HEARTBEAT — Periodic signal that this worker is alive.
     * If a worker stops sending heartbeats, it's presumed dead and its
     * in-flight jobs can be recovered by the stale job detector.
     */
    @Scheduled(fixedDelayString = "${worker.heartbeat-interval-ms:10000}")
    @Transactional
    public void sendHeartbeat() {
        if (workerEntity == null) return;

        workerEntity.setLastHeartbeatAt(Instant.now());
        workerEntity.setCurrentLoad(activeJobs.get());
        workerRepository.save(workerEntity);

        WorkerHeartbeat heartbeat = WorkerHeartbeat.builder()
                .worker(workerEntity)
                .heartbeatAt(Instant.now())
                .activeJobs(activeJobs.get())
                .build();
        heartbeatRepository.save(heartbeat);
    }

    /**
     * STALE WORKER DETECTOR — Marks workers as OFFLINE if heartbeat is stale.
     * Runs every 15 seconds. Stale threshold is configurable.
     */
    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void detectStaleWorkers() {
        Instant threshold = Instant.now().minusMillis(staleThresholdMs);
        int marked = workerRepository.markStaleWorkersOffline(
                WorkerStatus.OFFLINE, threshold, Instant.now());
        if (marked > 0) {
            log.warn("Marked {} stale workers as OFFLINE", marked);
        }
    }

    @Transactional
    protected void updateJobStatus(Long jobId, JobStatus newStatus) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        stateMachine.validateTransition(job.getStatus(), newStatus);
        job.setStatus(newStatus);
        jobRepository.save(job);
    }

    private void registerWorker() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "unknown";
        }

        workerEntity = Worker.builder()
                .hostname(hostname)
                .workerName("worker-" + UUID.randomUUID().toString().substring(0, 8))
                .status(WorkerStatus.ONLINE)
                .concurrencyLimit(maxConcurrency)
                .currentLoad(0)
                .lastHeartbeatAt(Instant.now())
                .build();

        // Worker needs an org — create a system org or skip for now
        // For the assessment, we'll handle this in the init data
    }
}
