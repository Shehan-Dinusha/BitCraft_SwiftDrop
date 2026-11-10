package com.swiftdrop.webhook.domain.repository;

import com.swiftdrop.webhook.domain.entity.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Read access to delivery jobs. Ingestion creates them through {@link IngestionJdbcRepository}. */
@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findByInboundEventEventId(UUID eventId);

    List<WebhookDelivery> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    List<WebhookDelivery> findByEndpointIdOrderByCreatedAtDesc(UUID endpointId);
}
