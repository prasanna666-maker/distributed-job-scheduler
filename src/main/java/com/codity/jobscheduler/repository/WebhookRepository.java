package com.codity.jobscheduler.repository;

import com.codity.jobscheduler.entity.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WebhookRepository extends JpaRepository<Webhook, Long> {
    List<Webhook> findByQueueIdAndIsActiveTrue(Long queueId);
    List<Webhook> findByQueueId(Long queueId);
}
