# Distributed Job Scheduler

A production-inspired distributed job scheduling system built with Java Spring Boot, MySQL, and React.

## Architecture

```
React Dashboard  ──REST──▶  Spring Boot Backend  ──JPA──▶  MySQL 8.x
                            ├── Auth (JWT + API Keys)
                            ├── Job Lifecycle Engine
                            ├── Worker Service (SKIP LOCKED)
                            ├── Retry Policy Engine
                            └── Webhook Dispatcher
```

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.3, Spring Data JPA, Spring Security |
| Database | MySQL 8.x (InnoDB, SKIP LOCKED) |
| Auth | JWT (jjwt), BCrypt, API Keys (SHA-256) |
| Frontend | React, Vite |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Testing | JUnit 5, Spring Boot Test |

## Prerequisites

- Java 17+
- Maven 3.8+
- MySQL 8.x
- Node.js 18+ (for frontend)

## Setup

### 1. Database

```sql
CREATE DATABASE job_scheduler;
CREATE DATABASE job_scheduler_test;  -- for integration tests
```

### 2. Backend

```bash
# Configure database credentials in src/main/resources/application.yml
# Default: root/root on localhost:3306

# Build and run
mvn clean install -DskipTests
mvn spring-boot:run

# The schema is auto-created on first run (schema.sql)
```

### 3. API Documentation

Visit [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) after starting the backend.

### 4. Frontend

```bash
cd frontend
npm install
npm run dev
# Open http://localhost:5173
```

## Quick Start

```bash
# 1. Register a user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@test.com","password":"password123","fullName":"Admin User"}'

# 2. Use the returned JWT token for subsequent requests
TOKEN="<jwt-token-from-response>"

# 3. Create an organization
curl -X POST http://localhost:8080/api/organizations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"My Org"}'

# 4. Create a project
curl -X POST http://localhost:8080/api/organizations/1/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"My Project"}'

# 5. Create a retry policy
curl -X POST http://localhost:8080/api/retry-policies \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Exponential","strategy":"EXPONENTIAL","maxRetries":5,"initialDelayMs":1000,"maxDelayMs":60000,"multiplier":2.0}'

# 6. Create a queue
curl -X POST http://localhost:8080/api/projects/1/queues \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"email-queue","priority":5,"concurrencyLimit":3,"retryPolicyId":1}'

# 7. Submit a job
curl -X POST http://localhost:8080/api/queues/1/jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"email.send","payload":{"to":"user@example.com","subject":"Welcome"}}'

# 8. Check queue stats
curl http://localhost:8080/api/queues/1/stats \
  -H "Authorization: Bearer $TOKEN"
```

## Running Tests

```bash
# Unit tests (retry backoff + state machine)
mvn test -pl . -Dtest="RetryPolicyTest,JobStateMachineTest"

# Integration test (concurrent job claiming — requires MySQL)
mvn test -pl . -Dtest="ConcurrentJobClaimTest" -Dspring.profiles.active=test
```

## Key Design Decisions

See [DESIGN_DECISIONS.md](./DESIGN_DECISIONS.md) for detailed trade-off analysis.

## Database Schema

See [database_schema.md](./database_schema.md) for the full ER diagram, DDL, indexes, and cascade rules.
