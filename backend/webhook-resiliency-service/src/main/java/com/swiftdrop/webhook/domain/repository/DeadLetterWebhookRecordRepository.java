package com.swiftdrop.webhook.domain.repository;

import com.swiftdrop.webhook.domain.entity.DeadLetterWebhookRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeadLetterWebhookRecordRepository extends JpaRepository<DeadLetterWebhookRecord, Long> {
    Optional<DeadLetterWebhookRecord> findByDlqId(UUID dlqId);
    List<DeadLetterWebhookRecord> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
    List<DeadLetterWebhookRecord> findByReplayedFalseOrderByCreatedAtDesc();
    List<DeadLetterWebhookRecord> findAllByOrderByCreatedAtDesc();
    long countByReplayedFalse();
}
