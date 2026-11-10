package com.swiftdrop.webhook.domain.repository;

import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {
    Optional<WebhookEndpoint> findByEndpointId(UUID endpointId);
    List<WebhookEndpoint> findByMerchantId(UUID merchantId);
    /** The only endpoint lookup ingestion uses: always scoped to one merchant. */
    List<WebhookEndpoint> findByMerchantIdAndActiveTrue(UUID merchantId);
}
