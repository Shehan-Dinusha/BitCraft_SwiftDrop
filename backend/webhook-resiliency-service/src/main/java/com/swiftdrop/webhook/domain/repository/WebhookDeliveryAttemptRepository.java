package com.swiftdrop.webhook.domain.repository;

import com.swiftdrop.webhook.domain.entity.WebhookDeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookDeliveryAttemptRepository extends JpaRepository<WebhookDeliveryAttempt, Long> {
    Optional<WebhookDeliveryAttempt> findByAttemptId(UUID attemptId);
    List<WebhookDeliveryAttempt> findByEndpointIdOrderByAttemptedAtDesc(UUID endpointId);
    List<WebhookDeliveryAttempt> findByMerchantIdOrderByAttemptedAtDesc(UUID merchantId);
}
