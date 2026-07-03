package com.codity.jobscheduler.dto;

import com.codity.jobscheduler.enums.RetryStrategy;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
import java.util.Map;

public class JobDto {

    @Data
    public static class CreateJobRequest {
        @NotBlank(message = "Job type is required")
        private String type;

        @NotNull(message = "Payload is required")
        private Map<String, Object> payload;

        private String idempotencyKey;

        @Min(value = 0, message = "Priority must be >= 0")
        private Integer priority = 0;

        /** ISO-8601 timestamp for delayed execution */
        private String scheduledAt;
    }

    @Data
    public static class CreateBatchJobRequest {
        @NotEmpty(message = "Jobs list cannot be empty")
        @Size(max = 100, message = "Batch size cannot exceed 100")
        private List<CreateJobRequest> jobs;
    }

    @Data
    public static class CreateScheduledJobRequest {
        @NotBlank(message = "Job type is required")
        private String jobType;

        @NotNull(message = "Payload is required")
        private Map<String, Object> payload;

        @NotBlank(message = "Cron expression is required")
        private String cronExpression;

        private String timezone = "UTC";
    }

    @Data
    public static class CreateQueueRequest {
        @NotBlank(message = "Queue name is required")
        private String name;

        @Min(value = 0)
        private Integer priority = 0;

        @Min(value = 1, message = "Concurrency limit must be at least 1")
        @Max(value = 100, message = "Concurrency limit cannot exceed 100")
        private Integer concurrencyLimit = 5;

        private Long retryPolicyId;
    }

    @Data
    public static class UpdateQueueRequest {
        @Min(value = 0)
        private Integer priority;

        @Min(value = 1) @Max(value = 100)
        private Integer concurrencyLimit;

        private Boolean isPaused;
        private Long retryPolicyId;
    }

    @Data
    public static class CreateRetryPolicyRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @NotNull(message = "Strategy is required")
        private RetryStrategy strategy;

        @Min(1) @Max(10)
        private Integer maxRetries = 3;

        @Min(100)
        private Long initialDelayMs = 1000L;

        @Min(1000)
        private Long maxDelayMs = 300000L;

        @DecimalMin("1.0") @DecimalMax("10.0")
        private Double multiplier = 2.0;
    }

    @Data
    public static class CreateProjectRequest {
        @NotBlank(message = "Project name is required")
        private String name;
        private String description;
    }

    @Data
    public static class CreateOrgRequest {
        @NotBlank(message = "Organization name is required")
        private String name;
    }

    @Data
    public static class QueueStatsResponse {
        private Long queueId;
        private String queueName;
        private long queued;
        private long running;
        private long completed;
        private long failed;
        private long dead;
        private long total;
        private boolean isPaused;
        private int concurrencyLimit;
    }
}
