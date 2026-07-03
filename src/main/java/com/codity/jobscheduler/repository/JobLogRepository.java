package com.codity.jobscheduler.repository;

import com.codity.jobscheduler.entity.JobLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobLogRepository extends JpaRepository<JobLog, Long> {
    Page<JobLog> findByJobId(Long jobId, Pageable pageable);
    Page<JobLog> findByExecutionId(Long executionId, Pageable pageable);
}
