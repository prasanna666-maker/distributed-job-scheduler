-- ============================================
-- Distributed Job Scheduler — MySQL 8.x DDL
-- ============================================

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS organizations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_organizations_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS organization_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    role ENUM('OWNER','ADMIN','MEMBER') NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_org_member (user_id, organization_id),
    INDEX idx_org_members_org (organization_id),
    CONSTRAINT fk_org_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_org_members_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_name (organization_id, name),
    CONSTRAINT fk_projects_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS retry_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    strategy ENUM('FIXED','LINEAR','EXPONENTIAL') NOT NULL,
    max_retries INT NOT NULL DEFAULT 3,
    initial_delay_ms BIGINT NOT NULL DEFAULT 1000,
    max_delay_ms BIGINT NOT NULL DEFAULT 300000,
    multiplier DOUBLE NOT NULL DEFAULT 2.0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS queues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    concurrency_limit INT NOT NULL DEFAULT 5,
    is_paused TINYINT(1) NOT NULL DEFAULT 0,
    retry_policy_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_queue_name (project_id, name),
    INDEX idx_queues_retry_policy (retry_policy_id),
    INDEX idx_queues_paused_priority (is_paused, priority),
    CONSTRAINT fk_queues_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_queues_retry_policy FOREIGN KEY (retry_policy_id) REFERENCES retry_policies(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    queue_id BIGINT NOT NULL,
    idempotency_key VARCHAR(255),
    type VARCHAR(255) NOT NULL,
    payload JSON NOT NULL,
    status ENUM('QUEUED','SCHEDULED','CLAIMED','RUNNING','COMPLETED','FAILED','RETRYING','DEAD') NOT NULL DEFAULT 'QUEUED',
    priority INT NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    scheduled_at TIMESTAMP NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_job_idempotency (queue_id, idempotency_key),
    INDEX idx_jobs_poll (queue_id, status, priority DESC, scheduled_at),
    INDEX idx_jobs_status (status),
    INDEX idx_jobs_created (created_at),
    INDEX idx_jobs_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS workers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT,
    hostname VARCHAR(255) NOT NULL,
    worker_name VARCHAR(255) NOT NULL,
    status ENUM('ONLINE','OFFLINE','DRAINING') NOT NULL DEFAULT 'ONLINE',
    concurrency_limit INT NOT NULL DEFAULT 5,
    current_load INT NOT NULL DEFAULT 0,
    last_heartbeat_at TIMESTAMP NULL,
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_workers_org (organization_id),
    INDEX idx_workers_status (status),
    INDEX idx_workers_heartbeat (last_heartbeat_at),
    CONSTRAINT fk_workers_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS job_executions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    worker_id BIGINT,
    attempt_number INT NOT NULL,
    status ENUM('RUNNING','COMPLETED','FAILED') NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NULL,
    duration_ms BIGINT,
    error_message TEXT,
    error_stack_trace TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_executions_job (job_id, attempt_number),
    INDEX idx_executions_worker (worker_id),
    INDEX idx_executions_started (started_at),
    CONSTRAINT fk_executions_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_executions_worker FOREIGN KEY (worker_id) REFERENCES workers(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS worker_heartbeats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    worker_id BIGINT NOT NULL,
    heartbeat_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active_jobs INT NOT NULL DEFAULT 0,
    cpu_usage DOUBLE,
    memory_usage_mb DOUBLE,
    INDEX idx_heartbeats_worker (worker_id, heartbeat_at DESC),
    CONSTRAINT fk_heartbeats_worker FOREIGN KEY (worker_id) REFERENCES workers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS job_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    execution_id BIGINT,
    level ENUM('INFO','WARN','ERROR','DEBUG') NOT NULL DEFAULT 'INFO',
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_logs_job (job_id, created_at),
    INDEX idx_logs_execution (execution_id),
    CONSTRAINT fk_logs_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_logs_execution FOREIGN KEY (execution_id) REFERENCES job_executions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scheduled_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    queue_id BIGINT NOT NULL,
    job_type VARCHAR(255) NOT NULL,
    payload JSON NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    next_fire_at TIMESTAMP NULL,
    last_fired_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_scheduled_active (is_active, next_fire_at),
    INDEX idx_scheduled_queue (queue_id),
    CONSTRAINT fk_scheduled_queue FOREIGN KEY (queue_id) REFERENCES queues(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dead_letter_queue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    queue_id BIGINT NOT NULL,
    failure_reason TEXT NOT NULL,
    last_error TEXT,
    total_attempts INT NOT NULL,
    dead_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    requeued_at TIMESTAMP NULL,
    UNIQUE KEY uk_dlq_job (job_id),
    INDEX idx_dlq_queue (queue_id, dead_at DESC),
    CONSTRAINT fk_dlq_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_dlq_queue FOREIGN KEY (queue_id) REFERENCES queues(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS api_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    key_prefix VARCHAR(8) NOT NULL,
    key_hash VARCHAR(255) NOT NULL,
    scopes JSON NOT NULL,
    rate_limit_per_min INT NOT NULL DEFAULT 60,
    last_used_at TIMESTAMP NULL,
    expires_at TIMESTAMP NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_api_key_hash (key_hash),
    INDEX idx_api_keys_user (user_id),
    INDEX idx_api_keys_org (organization_id),
    INDEX idx_api_keys_prefix (key_prefix),
    CONSTRAINT fk_api_keys_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_api_keys_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    actor_id BIGINT,
    actor_type ENUM('USER','API_KEY','SYSTEM') NOT NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id BIGINT NOT NULL,
    details JSON,
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_org_time (organization_id, created_at DESC),
    INDEX idx_audit_actor (actor_id),
    INDEX idx_audit_resource (resource_type, resource_id),
    INDEX idx_audit_action (action),
    CONSTRAINT fk_audit_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS webhooks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    queue_id BIGINT NOT NULL,
    url VARCHAR(2048) NOT NULL,
    secret VARCHAR(255) NOT NULL,
    events JSON NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    failure_count INT NOT NULL DEFAULT 0,
    last_triggered_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_webhooks_queue (queue_id, is_active),
    INDEX idx_webhooks_active (is_active),
    CONSTRAINT fk_webhooks_queue FOREIGN KEY (queue_id) REFERENCES queues(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
