package com.codity.jobscheduler.repository;

import com.codity.jobscheduler.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    Page<Project> findByOrganizationId(Long orgId, Pageable pageable);
    boolean existsByOrganizationIdAndName(Long orgId, String name);
}
