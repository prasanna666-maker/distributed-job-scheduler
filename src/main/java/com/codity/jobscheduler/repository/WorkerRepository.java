package com.codity.jobscheduler.repository;

import com.codity.jobscheduler.entity.Worker;
import com.codity.jobscheduler.enums.WorkerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
    List<Worker> findByOrganizationId(Long orgId);
    List<Worker> findByStatus(WorkerStatus status);

    @Modifying
    @Query("UPDATE Worker w SET w.status = :status, w.updatedAt = :now " +
           "WHERE w.lastHeartbeatAt < :threshold AND w.status = 'ONLINE'")
    int markStaleWorkersOffline(@Param("status") WorkerStatus status,
                                @Param("threshold") Instant threshold,
                                @Param("now") Instant now);
}
