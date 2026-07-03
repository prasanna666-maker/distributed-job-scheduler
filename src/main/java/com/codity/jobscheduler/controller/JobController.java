package com.codity.jobscheduler.controller;

import com.codity.jobscheduler.dto.JobDto;
import com.codity.jobscheduler.entity.*;
import com.codity.jobscheduler.enums.JobStatus;
import com.codity.jobscheduler.repository.*;
import com.codity.jobscheduler.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Jobs", description = "Job creation, lifecycle management, and querying")
public class JobController {

    private final JobService jobService;
    private final JobLogRepository jobLogRepository;
    private final JobExecutionRepository executionRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final WorkerRepository workerRepository;

    // --- Job Creation ---

    @PostMapping("/queues/{queueId}/jobs")
    @Operation(summary = "Create an immediate or delayed job")
    public ResponseEntity<Job> createJob(
            @PathVariable Long queueId,
            @Valid @RequestBody JobDto.CreateJobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.createJob(queueId, request));
    }

    @PostMapping("/queues/{queueId}/jobs/batch")
    @Operation(summary = "Create multiple jobs in a single batch")
    public ResponseEntity<List<Job>> createBatchJobs(
            @PathVariable Long queueId,
            @Valid @RequestBody JobDto.CreateBatchJobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.createBatchJobs(queueId, request));
    }

    @PostMapping("/queues/{queueId}/scheduled-jobs")
    @Operation(summary = "Create a recurring scheduled job (cron)")
    public ResponseEntity<ScheduledJob> createScheduledJob(
            @PathVariable Long queueId,
            @Valid @RequestBody JobDto.CreateScheduledJobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.createScheduledJob(queueId, request));
    }

    // --- Job Queries ---

    @GetMapping("/queues/{queueId}/jobs")
    @Operation(summary = "List jobs in a queue with optional status filter")
    public ResponseEntity<Page<Job>> getJobs(
            @PathVariable Long queueId,
            @RequestParam(required = false) JobStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(jobService.getJobsByQueue(queueId, status, pageable));
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Get job details")
    public ResponseEntity<Job> getJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(jobService.getJob(jobId));
    }

    // --- Queue Stats ---

    @GetMapping("/queues/{queueId}/stats")
    @Operation(summary = "Get queue statistics (job counts by status)")
    public ResponseEntity<JobDto.QueueStatsResponse> getQueueStats(@PathVariable Long queueId) {
        return ResponseEntity.ok(jobService.getQueueStats(queueId));
    }

    // --- Execution Logs ---

    @GetMapping("/jobs/{jobId}/executions")
    @Operation(summary = "List execution attempts for a job")
    public ResponseEntity<Page<JobExecution>> getExecutions(
            @PathVariable Long jobId, Pageable pageable) {
        return ResponseEntity.ok(executionRepository.findByJobId(jobId, pageable));
    }

    @GetMapping("/jobs/{jobId}/logs")
    @Operation(summary = "Get job logs")
    public ResponseEntity<Page<JobLog>> getJobLogs(
            @PathVariable Long jobId, Pageable pageable) {
        return ResponseEntity.ok(jobLogRepository.findByJobId(jobId, pageable));
    }

    // --- Dead Letter Queue ---

    @GetMapping("/queues/{queueId}/dlq")
    @Operation(summary = "Browse dead letter queue entries for a queue")
    public ResponseEntity<Page<DeadLetterEntry>> getDlq(
            @PathVariable Long queueId, Pageable pageable) {
        return ResponseEntity.ok(deadLetterRepository.findByQueueId(queueId, pageable));
    }

    @PostMapping("/jobs/{jobId}/requeue")
    @Operation(summary = "Requeue a job from the dead letter queue")
    public ResponseEntity<Job> requeueFromDlq(@PathVariable Long jobId) {
        return ResponseEntity.ok(jobService.requeueFromDlq(jobId));
    }

    // --- Workers ---

    @GetMapping("/workers")
    @Operation(summary = "List all workers")
    public ResponseEntity<List<Worker>> getWorkers() {
        return ResponseEntity.ok(workerRepository.findAll());
    }
}
