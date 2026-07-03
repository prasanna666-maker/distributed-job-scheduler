package com.codity.jobscheduler.repository;

import com.codity.jobscheduler.entity.Job;
import com.codity.jobscheduler.enums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {

    /**
     * ATOMIC JOB CLAIMING — The most critical query in the entire system.
     *
     * Uses SELECT ... FOR UPDATE SKIP LOCKED to atomically claim the next available job:
     * - FOR UPDATE: Acquires an exclusive row-level lock on the selected row
     * - SKIP LOCKED: Skips rows that are already locked by other workers/transactions
     *
     * This guarantees:
     * 1. No two workers can claim the same job (exclusive lock)
     * 2. Workers don't block each other (skip locked rows, grab different ones)
     * 3. No duplicate execution under any concurrency scenario
     *
     * The query uses the composite index idx_jobs_poll (queue_id, status, priority DESC, scheduled_at)
     * to efficiently find candidates without a full table scan.
     *
     * IMPORTANT: This must be called within an active transaction (@Transactional).
     * The lock is released when the transaction commits.
     */
    @Query(value = """
            SELECT * FROM jobs
            WHERE queue_id = :queueId
              AND status = 'QUEUED'
              AND (scheduled_at IS NULL OR scheduled_at <= :now)
            ORDER BY priority DESC, created_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Job> claimNextJob(@Param("queueId") Long queueId, @Param("now") Instant now);

    /**
     * Transition jobs from SCHEDULED to QUEUED when their scheduled_at time has arrived.
     * Called periodically by the scheduler service.
     */
    @Modifying
    @Query("UPDATE Job j SET j.status = 'QUEUED', j.updatedAt = :now " +
           "WHERE j.status = com.codity.jobscheduler.enums.JobStatus.SCHEDULED " +
           "AND j.scheduledAt <= :now")
    int activateScheduledJobs(@Param("now") Instant now);

    Page<Job> findByQueueId(Long queueId, Pageable pageable);

    Page<Job> findByQueueIdAndStatus(Long queueId, JobStatus status, Pageable pageable);

    Page<Job> findByStatus(JobStatus status, Pageable pageable);

    Optional<Job> findByQueueIdAndIdempotencyKey(Long queueId, String idempotencyKey);

    @Query("SELECT j.status, COUNT(j) FROM Job j WHERE j.queue.id = :queueId GROUP BY j.status")
    List<Object[]> countByStatusForQueue(@Param("queueId") Long queueId);

    @Query("SELECT COUNT(j) FROM Job j WHERE j.queue.id = :queueId AND j.status = :status")
    long countByQueueIdAndStatus(@Param("queueId") Long queueId, @Param("status") JobStatus status);

    /**
     * Find jobs stuck in CLAIMED or RUNNING state with stale workers (no heartbeat).
     * These are candidates for recovery — worker likely crashed.
     */
    @Query(value = """
            SELECT j.* FROM jobs j
            JOIN job_executions je ON je.job_id = j.id
            JOIN workers w ON je.worker_id = w.id
            WHERE j.status IN ('CLAIMED', 'RUNNING')
              AND w.last_heartbeat_at < :staleThreshold
            ORDER BY j.created_at ASC
            """, nativeQuery = true)
    List<Job> findStuckJobs(@Param("staleThreshold") Instant staleThreshold);
}
