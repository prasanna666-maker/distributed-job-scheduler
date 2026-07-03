# Design Decisions

## 1. Why SELECT ... FOR UPDATE SKIP LOCKED over a Message Queue

| Approach | Pros | Cons |
|---|---|---|
| **SKIP LOCKED (chosen)** | Zero external dependencies, MySQL is single source of truth, atomic claim in one TX, simpler ops/deployment | Limited to MySQL throughput (~10K claims/sec), not suitable for million-msg/sec workloads |
| RabbitMQ/Kafka | Higher throughput, built-in retry/DLQ, consumer groups | Extra infra dependency, message-DB consistency problem, operational complexity |

**Decision:** For this assessment's scale, MySQL-native claiming is sufficient and demonstrates deeper understanding of database concurrency primitives. In production at higher scale, I'd introduce a message queue and use the DB as the persistence layer only.

## 2. Why These Indexes

### The Critical Poll Index: `idx_jobs_poll (queue_id, status, priority DESC, scheduled_at)`

This composite index serves the claim query:
```sql
WHERE queue_id = ? AND status = 'QUEUED' AND (scheduled_at IS NULL OR scheduled_at <= NOW())
ORDER BY priority DESC, created_at ASC
```

- `queue_id` + `status` — equality predicates, leftmost columns
- `priority DESC` — ordering column
- `scheduled_at` — range filter for delayed jobs

Without this index, every poll cycle (every 2 seconds × N workers) would full-table-scan the jobs table.

### Why Not Index on `created_at` in the Poll Index?

The `ORDER BY priority DESC, created_at ASC` uses two columns. MySQL can use the index for `priority DESC` but has to sort by `created_at` for ties. Adding `created_at` to the index would make it 5 columns, which has diminishing returns. The LIMIT 1 + SKIP LOCKED means the sort is on a very small candidate set.

## 3. Denormalization Choices

| Field | Trade-off |
|---|---|
| `jobs.attempt_count` | Avoids `COUNT(*)` on `job_executions` every poll cycle. Cost: must maintain in same TX as execution insert. |
| `jobs.max_retries` | Snapshot from retry policy at job creation. If policy changes later, in-flight jobs use original value. This is intentional — changing retry policy mid-flight is a footgun. |
| `workers.current_load` | Avoids `COUNT(*)` on running executions per worker. Inc/dec atomically in claim/complete. |
| `workers.last_heartbeat_at` | Avoids `MAX()` on `worker_heartbeats`. Updated in same TX as heartbeat insert. |
| `dead_letter_queue.queue_id` | Allows DLQ browsing by queue without JOIN through jobs. One extra column. |

## 4. Cascade Rules

**CASCADE on structural hierarchy** (org → project → queue → job): Deleting an org should clean up everything underneath. This matches business semantics — an org's data belongs to the org.

**SET NULL on workers → job_executions**: When a worker is deregistered, we want to keep the execution history for debugging. Setting `worker_id` to NULL preserves the record while acknowledging the worker no longer exists.

**SET NULL on retry_policies → queues**: Deleting a retry policy shouldn't delete queues. The queue falls back to default retry behavior (exponential backoff with sensible defaults).

## 5. State Machine Design

**Why CLAIMED as a separate state (not just QUEUED → RUNNING)?**

The CLAIMED state exists because there's a gap between locking the row and actually beginning execution. If a worker crashes between claiming and starting execution, the job is in CLAIMED state — and the stale heartbeat detector can identify and recover it. Without CLAIMED, a crash during this window would leave the job in QUEUED but with an orphaned lock.

**Why RETRYING → QUEUED (not RETRYING → SCHEDULED)?**

When a job retries, it goes back to QUEUED with a `scheduled_at` in the future. The poll query filters `scheduled_at <= NOW()`, so the job won't be picked up until the backoff delay has elapsed. This reuses the existing poll mechanism instead of needing a separate scheduler for retries.

## 6. JWT + API Keys (Dual Auth)

| Audience | Auth Method | Rationale |
|---|---|---|
| Interactive users (dashboard) | JWT | Short-lived, stateless, standard for SPAs |
| Programmatic access (workers, CI) | API Key | No token refresh needed, per-key rate limits, scope-based permissions |

The API key is hashed (SHA-256) and stored; the plaintext is shown once at creation. This mirrors GitHub's personal access token model.

## 7. Audit Log as Append-Only Table

The `audit_logs` table has NO UPDATE or DELETE operations in application code. It's an immutable ledger:
- Every write operation (job create, queue pause, DLQ requeue) generates an audit entry
- Actor tracking supports USER, API_KEY, and SYSTEM actors
- IP address is captured for security forensics
- Old entries can be archived via scheduled retention job

## 8. Worker Graceful Shutdown

The shutdown sequence is:
1. `@PreDestroy` sets `draining = true` — poll loop stops immediately
2. Worker status set to DRAINING in DB — visible to the dashboard
3. `ExecutorService.shutdown()` — no new tasks accepted
4. `awaitTermination(60s)` — wait for in-flight jobs to finish
5. Worker status set to OFFLINE

This prevents the common failure mode of abruptly killing a worker while jobs are mid-execution, which would leave them in RUNNING state until the stale heartbeat detector recovers them.
