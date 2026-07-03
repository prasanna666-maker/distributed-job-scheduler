package com.codity.jobscheduler.repository;

import com.codity.jobscheduler.entity.OrganizationMember;
import com.codity.jobscheduler.enums.OrgRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {
    Optional<OrganizationMember> findByUserIdAndOrganizationId(Long userId, Long orgId);
    List<OrganizationMember> findByUserId(Long userId);
    List<OrganizationMember> findByOrganizationId(Long orgId);
    boolean existsByUserIdAndOrganizationIdAndRole(Long userId, Long orgId, OrgRole role);
    boolean existsByUserIdAndOrganizationId(Long userId, Long orgId);
}
