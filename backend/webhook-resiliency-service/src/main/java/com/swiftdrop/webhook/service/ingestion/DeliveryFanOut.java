package com.swiftdrop.webhook.service.ingestion;

import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import com.swiftdrop.webhook.domain.repository.IngestionJdbcRepository;
import com.swiftdrop.webhook.domain.repository.WebhookEndpointRepository;
import com.swiftdrop.webhook.service.subscription.EventSubscriptionPatterns;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Creates one delivery job per endpoint of the event's merchant that subscribes to the event type, <b>including
 * paused (inactive) endpoints</b> (decision D8): an event arriving during a pause is queued as a {@code PENDING} job
 * and is delivered after the endpoint is re-enabled. Paused endpoints are skipped when jobs are <em>claimed</em>
 * ({@code DeliveryJobRepository.claimDue}), never here. Deleted endpoints no longer exist and get nothing; nothing is
 * sent from ingestion.
 *
 * <p>Tenant isolation: endpoints are queried by merchant, never globally, and the composite foreign keys on
 * {@code webhook_deliveries} reject any row whose merchant differs from the event's or the endpoint's. There is
 * no code path that selects endpoints without a merchant.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class DeliveryFanOut {

    private final WebhookEndpointRepository endpointRepository;
    private final IngestionJdbcRepository ingestionRepository;

    /**
     * Must run inside the ingestion transaction, after the event was marked {@code RESOLVED} for this merchant
     * (the composite foreign key requires it).
     *
     * @return number of deliveries created (existing ones are not counted)
     */
    int createDeliveries(UUID eventId, String eventType, UUID merchantId) {
        int created = 0;
        for (WebhookEndpoint endpoint : endpointRepository.findByMerchantId(merchantId)) {
            if (!EventSubscriptionPatterns.parseStored(endpoint.getSubscribedEvents()).matches(eventType)) {
                continue;
            }
            UUID deliveryId = UUID.randomUUID();
            if (ingestionRepository.insertDeliveryIfAbsent(deliveryId, eventId, endpoint.getEndpointId(), merchantId)) {
                created++;
                log.debug("Created delivery deliveryId={} eventId={} endpointId={} merchantId={}",
                        deliveryId, eventId, endpoint.getEndpointId(), merchantId);
            }
        }
        return created;
    }
}
