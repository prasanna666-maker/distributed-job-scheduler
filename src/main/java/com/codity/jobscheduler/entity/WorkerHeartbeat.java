package com.codity.jobscheduler.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "worker_heartbeats")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerHeartbeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(name = "heartbeat_at", nullable = false)
    private Instant heartbeatAt;

    @Column(name = "active_jobs", nullable = false)
    private Integer activeJobs;

    @Column(name = "cpu_usage")
    private Double cpuUsage;

    @Column(name = "memory_usage_mb")
    private Double memoryUsageMb;

    @PrePersist
    protected void onCreate() {
        if (heartbeatAt == null) heartbeatAt = Instant.now();
        if (activeJobs == null) activeJobs = 0;
    }
}
