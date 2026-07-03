package com.codity.jobscheduler.repository;

import com.codity.jobscheduler.entity.WorkerHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerHeartbeatRepository extends JpaRepository<WorkerHeartbeat, Long> {
}
