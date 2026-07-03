package com.codity.jobscheduler.repository;

import com.codity.jobscheduler.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByKeyHash(String keyHash);
    List<ApiKey> findByUserIdAndOrganizationId(Long userId, Long orgId);
}
