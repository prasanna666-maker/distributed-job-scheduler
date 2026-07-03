# Distributed Job Scheduler — Implementation Plan

## Evaluation Weight → Effort Allocation

| Category | Weight | Effort | Key Deliverables |
|---|---|---|---|
| System Architecture | 20% | High | Component diagram, clean layering, separation of concerns |
| Database Design | 20% | High | Normalized schema, proper indexes, cascades, ER diagram |
| Backend Engineering | 20% | High | Spring Boot, clean service/repo layers, state machine |
| Reliability & Concurrency | 15% | High | `SELECT FOR UPDATE SKIP LOCKED`, heartbeats, idempotency |
| Frontend & UX | 10% | Medium | React dashboard, polling, functional over flashy |
| API Design | 5% | Medium | RESTful, pagination, filtering, OpenAPI spec |
| Documentation | 5% | Medium | README, architecture doc, design decisions |
| Testing | 5% | Medium | Retry calc unit test, concurrent claim integration test |

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    React Frontend                       │
│  (Dashboard, Job Explorer, Queue Config, Metrics)       │
└─────────────────────┬───────────────────────────────────┘
                      │ REST API (JSON)
┌─────────────────────▼───────────────────────────────────┐
│               Spring Boot Backend                       │
│  ┌──────────┐ ┌──────────┐ ┌───────────┐ ┌───────────┐ │
│  │ Auth     │ │ Job      │ │ Queue     │ │ Scheduler │ │
│  │ (JWT)    │ │ Service  │ │ Service   │ │ Service   │ │
│  └──────────┘ └──────────┘ └───────────┘ └───────────┘ │
│  ┌──────────────────────────────────────────────────┐   │
│  │            Worker Service (embedded)              │   │
│  │  Poll → Claim (FOR UPDATE SKIP LOCKED) → Execute │   │
│  │  Heartbeat thread │ Graceful shutdown hook        │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────┬───────────────────────────────────┘
                      │ JPA / Hibernate
┌─────────────────────▼───────────────────────────────────┐
│                     MySQL 8.x                           │
│  (InnoDB, row-level locking, SKIP LOCKED support)       │
└─────────────────────────────────────────────────────────┘
```

## Execution Order & Checkpoints

### Phase 1: Database Schema (STOP FOR REVIEW)
- [ ] Design all 12 tables with PKs, FKs, indexes, cascades
- [ ] Document denormalization trade-offs
- [ ] Generate ER diagram description
- **⏸ Present to user for review before proceeding**

### Phase 2: Core Backend
- [ ] Spring Boot project scaffold (Maven, dependencies)
- [ ] JPA entities matching approved schema
- [ ] JWT authentication (login, register, token validation)
- [ ] Organization → Project → Queue hierarchy CRUD
- [ ] Job creation APIs (immediate, delayed, scheduled, cron, batch)
- [ ] Job state machine with explicit transitions
- [ ] Retry policy engine (fixed, linear, exponential)
- [ ] Idempotency key enforcement on job submission

### Phase 3: Worker Service
- [ ] Queue polling with `SELECT ... FOR UPDATE SKIP LOCKED`
- [ ] Configurable concurrency (thread pool per worker)
- [ ] Periodic heartbeat updates
- [ ] Graceful shutdown via `@PreDestroy` / shutdown hook
- [ ] Execution logging (worker, timestamps, duration, errors)
- [ ] Dead letter queue routing on max retries

### Phase 4: REST API Polish
- [ ] Global exception handler with consistent error schema
- [ ] Pagination + filtering on list endpoints
- [ ] Request validation (`@Valid`, custom validators)
- [ ] Logging middleware (request/response)
- [ ] OpenAPI/Swagger documentation

### Phase 5: React Frontend
- [ ] Dashboard: queue health, worker status
- [ ] Job explorer with filters + pagination
- [ ] Execution logs viewer
- [ ] Queue config screen (pause/resume, retry policy)
- [ ] Throughput/metrics charts (polling-based)

### Phase 6: Tests
- [ ] Unit: retry backoff calculation (fixed, linear, exponential)
- [ ] Unit: state machine transition validation
- [ ] **Integration: two concurrent workers cannot claim the same job** (critical)

### Phase 7: Documentation
- [ ] README with setup instructions
- [ ] Architecture diagram (component-level, Mermaid)
- [ ] ER diagram (Mermaid)
- [ ] Design decisions document
- [ ] API docs (Swagger UI auto-generated)

## Key Technical Decisions (Preview)

| Decision | Rationale |
|---|---|
| `SKIP LOCKED` over message queue | No external dependency (RabbitMQ/Kafka), MySQL-native, simpler ops, sufficient for this scale |
| Embedded worker (same JVM) | Simpler deployment for assessment; comments explain how to extract to separate service |
| Quartz for cron scheduling | Battle-tested, Spring-integrated, handles misfires |
| JWT over session | Stateless, scales horizontally, standard for REST APIs |
| InnoDB row-level locking | Required for `SKIP LOCKED`; MyISAM uses table locks |
