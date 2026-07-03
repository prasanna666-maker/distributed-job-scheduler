package com.codity.jobscheduler.entity;

import com.codity.jobscheduler.enums.WorkerStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "workers")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = true)
    private Organization organization;

    @Column(nullable = false)
    private String hostname;

    @Column(name = "worker_name", nullable = false)
    private String workerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkerStatus status;

    @Column(name = "concurrency_limit", nullable = false)
    private Integer concurrencyLimit;

    @Column(name = "current_load", nullable = false)
    private Integer currentLoad;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        registeredAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) status = WorkerStatus.ONLINE;
        if (concurrencyLimit == null) concurrencyLimit = 5;
        if (currentLoad == null) currentLoad = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
