package com.codity.jobscheduler.service;

import com.codity.jobscheduler.entity.RetryPolicy;
import com.codity.jobscheduler.enums.RetryStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for retry backoff calculation.
 * Tests all three strategies: FIXED, LINEAR, EXPONENTIAL.
 * Verifies max_delay_ms cap is respected.
 */
class RetryPolicyTest {

    @Nested
    @DisplayName("FIXED backoff strategy")
    class FixedBackoff {

        @Test
        @DisplayName("should return constant delay regardless of attempt number")
        void fixedDelay() {
            RetryPolicy policy = buildPolicy(RetryStrategy.FIXED, 1000, 300000, 2.0);

            assertEquals(1000, policy.calculateDelay(1));
            assertEquals(1000, policy.calculateDelay(2));
            assertEquals(1000, policy.calculateDelay(5));
            assertEquals(1000, policy.calculateDelay(10));
        }
    }

    @Nested
    @DisplayName("LINEAR backoff strategy")
    class LinearBackoff {

        @Test
        @DisplayName("should increase delay linearly with each attempt")
        void linearDelay() {
            RetryPolicy policy = buildPolicy(RetryStrategy.LINEAR, 1000, 300000, 2.0);

            // delay = initialDelayMs + (attempt * multiplier * 1000)
            assertEquals(3000, policy.calculateDelay(1));   // 1000 + (1 * 2.0 * 1000)
            assertEquals(5000, policy.calculateDelay(2));   // 1000 + (2 * 2.0 * 1000)
            assertEquals(7000, policy.calculateDelay(3));   // 1000 + (3 * 2.0 * 1000)
        }

        @Test
        @DisplayName("should cap at max_delay_ms")
        void linearDelayCapped() {
            RetryPolicy policy = buildPolicy(RetryStrategy.LINEAR, 1000, 5000, 2.0);

            assertEquals(3000, policy.calculateDelay(1));
            assertEquals(5000, policy.calculateDelay(2));  // would be 5000, equals cap
            assertEquals(5000, policy.calculateDelay(5));  // capped
            assertEquals(5000, policy.calculateDelay(100)); // still capped
        }
    }

    @Nested
    @DisplayName("EXPONENTIAL backoff strategy")
    class ExponentialBackoff {

        @Test
        @DisplayName("should double delay with each attempt (multiplier=2)")
        void exponentialDelay() {
            RetryPolicy policy = buildPolicy(RetryStrategy.EXPONENTIAL, 1000, 300000, 2.0);

            // delay = initialDelayMs * multiplier^(attempt-1)
            assertEquals(1000, policy.calculateDelay(1));   // 1000 * 2^0 = 1000
            assertEquals(2000, policy.calculateDelay(2));   // 1000 * 2^1 = 2000
            assertEquals(4000, policy.calculateDelay(3));   // 1000 * 2^2 = 4000
            assertEquals(8000, policy.calculateDelay(4));   // 1000 * 2^3 = 8000
        }

        @Test
        @DisplayName("should cap at max_delay_ms")
        void exponentialDelayCapped() {
            RetryPolicy policy = buildPolicy(RetryStrategy.EXPONENTIAL, 1000, 5000, 2.0);

            assertEquals(1000, policy.calculateDelay(1));
            assertEquals(2000, policy.calculateDelay(2));
            assertEquals(4000, policy.calculateDelay(3));
            assertEquals(5000, policy.calculateDelay(4));   // would be 8000, capped at 5000
            assertEquals(5000, policy.calculateDelay(10));  // still capped
        }

        @Test
        @DisplayName("should work with custom multiplier")
        void exponentialCustomMultiplier() {
            RetryPolicy policy = buildPolicy(RetryStrategy.EXPONENTIAL, 500, 300000, 3.0);

            assertEquals(500, policy.calculateDelay(1));    // 500 * 3^0 = 500
            assertEquals(1500, policy.calculateDelay(2));   // 500 * 3^1 = 1500
            assertEquals(4500, policy.calculateDelay(3));   // 500 * 3^2 = 4500
        }
    }

    private RetryPolicy buildPolicy(RetryStrategy strategy, long initialMs, long maxMs, double multiplier) {
        return RetryPolicy.builder()
                .strategy(strategy)
                .initialDelayMs(initialMs)
                .maxDelayMs(maxMs)
                .multiplier(multiplier)
                .maxRetries(5)
                .name("test-policy")
                .build();
    }
}
