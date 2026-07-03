package com.codity.jobscheduler.repository;

import com.codity.jobscheduler.entity.JobExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {
    Page<JobExecution> findByJobId(Long jobId, Pageable pageable);
}
