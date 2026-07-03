package com.codity.jobscheduler.repository;

import com.codity.jobscheduler.entity.Queue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface QueueRepository extends JpaRepository<Queue, Long> {
    Page<Queue> findByProjectId(Long projectId, Pageable pageable);
    boolean existsByProjectIdAndName(Long projectId, String name);

    /**
     * Finds all active (non-paused) queues, ordered by priority descending.
     * Used by the worker service to determine which queues to poll.
     */
    @Query("SELECT q FROM Queue q WHERE q.isPaused = false ORDER BY q.priority DESC")
    List<Queue> findActiveQueuesOrderedByPriority();
}
