package com.codity.jobscheduler.repository;

import com.codity.jobscheduler.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByOrganizationId(Long orgId, Pageable pageable);
    Page<AuditLog> findByOrganizationIdAndResourceTypeAndResourceId(
            Long orgId, String resourceType, Long resourceId, Pageable pageable);
}
