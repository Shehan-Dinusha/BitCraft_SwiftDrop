package com.swiftdrop.webhook.domain.repository;

import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {
}
