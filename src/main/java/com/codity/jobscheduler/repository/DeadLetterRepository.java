package com.codity.jobscheduler.repository;

import com.codity.jobscheduler.entity.DeadLetterEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DeadLetterRepository extends JpaRepository<DeadLetterEntry, Long> {
    Page<DeadLetterEntry> findByQueueId(Long queueId, Pageable pageable);
    Optional<DeadLetterEntry> findByJobId(Long jobId);
}
