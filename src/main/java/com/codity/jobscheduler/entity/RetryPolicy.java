package com.codity.jobscheduler.entity;

import com.codity.jobscheduler.enums.RetryStrategy;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "retry_policies")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetryPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RetryStrategy strategy;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    @Column(name = "initial_delay_ms", nullable = false)
    private Long initialDelayMs;

    @Column(name = "max_delay_ms", nullable = false)
    private Long maxDelayMs;

    @Column(nullable = false)
    private Double multiplier;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (maxRetries == null) maxRetries = 3;
        if (initialDelayMs == null) initialDelayMs = 1000L;
        if (maxDelayMs == null) maxDelayMs = 300000L;
        if (multiplier == null) multiplier = 2.0;
    }

    /**
     * Calculates the delay in milliseconds for the given attempt number.
     *
     * FIXED:       delay = initialDelayMs
     * LINEAR:      delay = min(initialDelayMs + (attempt * multiplier * 1000), maxDelayMs)
     * EXPONENTIAL: delay = min(initialDelayMs * multiplier^(attempt-1), maxDelayMs)
     */
    public long calculateDelay(int attempt) {
        long delay = switch (strategy) {
            case FIXED -> initialDelayMs;
            case LINEAR -> initialDelayMs + (long) (attempt * multiplier * 1000);
            case EXPONENTIAL -> (long) (initialDelayMs * Math.pow(multiplier, attempt - 1));
        };
        return Math.min(delay, maxDelayMs);
    }
}
