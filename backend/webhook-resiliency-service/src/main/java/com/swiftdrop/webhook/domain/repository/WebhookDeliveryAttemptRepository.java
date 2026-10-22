package com.swiftdrop.webhook.domain.repository;

import com.swiftdrop.webhook.domain.entity.WebhookDeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeliveryAttemptRepository extends JpaRepository<WebhookDeliveryAttempt, Long> {
}
