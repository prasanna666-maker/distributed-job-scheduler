package com.codity.jobscheduler.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "dead_letter_queue")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeadLetterEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_id", nullable = false)
    private Queue queue;

    @Column(name = "failure_reason", nullable = false, columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "total_attempts", nullable = false)
    private Integer totalAttempts;

    @Column(name = "dead_at", nullable = false, updatable = false)
    private Instant deadAt;

    @Column(name = "requeued_at")
    private Instant requeuedAt;

    @PrePersist
    protected void onCreate() {
        deadAt = Instant.now();
    }
}
