package com.codity.jobscheduler.repository;

import com.codity.jobscheduler.entity.ScheduledJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, Long> {
    List<ScheduledJob> findByQueueId(Long queueId);

    @Query("SELECT sj FROM ScheduledJob sj WHERE sj.isActive = true AND sj.nextFireAt <= :now")
    List<ScheduledJob> findDueScheduledJobs(@Param("now") Instant now);
}
