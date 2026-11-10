package com.swiftdrop.webhook.service.ingestion;

import com.swiftdrop.webhook.domain.entity.OrderMerchantMapping;
import com.swiftdrop.webhook.domain.repository.IngestionJdbcRepository;
import com.swiftdrop.webhook.domain.repository.IngestionJdbcRepository.AwaitingEvent;
import com.swiftdrop.webhook.domain.repository.OrderMerchantMappingRepository;
import com.swiftdrop.webhook.exception.NonRetryableIngestionException;
import com.swiftdrop.webhook.messaging.decoder.DecodedInboundEvent;
import com.swiftdrop.webhook.service.subscription.WebhookEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Transactional inbox: persists an inbound event, resolves its merchant and creates delivery jobs, all in one
 * database transaction. No network calls happen here; the broker acknowledgement follows the commit (see
 * {@code RabbitMQConfig}).
 *
 * <h2>Merchant resolution</h2>
 * {@code order.created} names its merchant and records it in {@code order_merchant_map} under the canonical
 * order id. {@code parcel.status.changed} carries no merchant, so it is resolved through that map. If the map has
 * no row yet (the status event overtook its {@code order.created}), the event is parked as
 * {@code AWAITING_MERCHANT} and acknowledged: it is neither broadcast, dropped nor requeued. The arrival of the
 * order's {@code order.created} resolves every parked event of that order in the same transaction.
 *
 * <h2>Idempotency</h2>
 * The inbox primary key decides whether an event id is new; a redelivery or a concurrent duplicate writes
 * nothing. Delivery creation is additionally guarded by the unique (event, endpoint) index.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundEventIngestionService {

    private final IngestionJdbcRepository ingestionRepository;
    private final OrderMerchantMappingRepository orderMerchantMappingRepository;
    private final DeliveryFanOut deliveryFanOut;

    @Transactional
    public IngestionResult ingest(DecodedInboundEvent event) {
        // First statement of the transaction: everything below reads state committed by earlier holders.
        ingestionRepository.lockOrder(event.orderId());

        boolean isNew = ingestionRepository.insertInboundEventIfAbsent(event.eventId(), event.type().eventType(),
                event.schemaVersion(), event.sourceService(), event.correlationId(), event.orderId(),
                event.payload().toString(), event.occurredAt());
        if (!isNew) {
            return IngestionResult.duplicate();
        }

        if (event.type() == WebhookEventType.ORDER_CREATED) {
            return ingestOrderCreated(event);
        }

        Optional<OrderMerchantMapping> ownership = orderMerchantMappingRepository.findById(event.orderId());
        if (ownership.isEmpty()) {
            log.info("Parked event until its order is known eventId={} eventType={} orderId={}",
                    event.eventId(), event.type().eventType(), event.orderId());
            return new IngestionResult(IngestionResult.Outcome.AWAITING_MERCHANT, 0, 0, 0);
        }
        UUID merchantId = ownership.get().getMerchantId();
        int created = resolve(event.eventId(), event.type().eventType(), merchantId);
        return new IngestionResult(outcomeFor(merchantId), created, 0, 0);
    }

    private IngestionResult ingestOrderCreated(DecodedInboundEvent event) {
        UUID claimedMerchantId = event.claimedMerchantId();
        ingestionRepository.insertOrderOwnershipIfAbsent(event.orderId(), claimedMerchantId,
                event.trackingNumber(), event.eventId());
        OrderMerchantMapping ownership = orderMerchantMappingRepository.findById(event.orderId())
                .orElseThrow(() -> new IllegalStateException("Order ownership missing after insert"));
        if (!Objects.equals(ownership.getMerchantId(), claimedMerchantId)) {
            // Two order.created events disagree about who owns the order. Neither claim can be trusted to route
            // customer data, so the event is rejected to the broker DLQ (rolling back its inbox row).
            log.error("Conflicting order ownership eventId={} orderId={} recordedBySourceEvent={}",
                    event.eventId(), event.orderId(), ownership.getSourceEventId());
            throw new NonRetryableIngestionException("order.created conflicts with the recorded owner of its order");
        }

        int created = resolve(event.eventId(), event.type().eventType(), claimedMerchantId);

        int released = 0;
        int releasedDeliveries = 0;
        for (AwaitingEvent parked : ingestionRepository.findAwaitingEventsForUpdate(event.orderId())) {
            releasedDeliveries += resolve(parked.eventId(), parked.eventType(), claimedMerchantId);
            released++;
        }
        if (released > 0) {
            log.info("Resolved parked events orderId={} released={} deliveries={}",
                    event.orderId(), released, releasedDeliveries);
        }
        return new IngestionResult(outcomeFor(claimedMerchantId), created, released, releasedDeliveries);
    }

    /** Marks the event resolved and, for a merchant order, creates its deliveries. */
    private int resolve(UUID eventId, String eventType, UUID merchantId) {
        if (merchantId == null) {
            ingestionRepository.markNoMerchant(eventId);
            return 0;
        }
        ingestionRepository.markResolved(eventId, merchantId);
        return deliveryFanOut.createDeliveries(eventId, eventType, merchantId);
    }

    private static IngestionResult.Outcome outcomeFor(UUID merchantId) {
        return merchantId == null ? IngestionResult.Outcome.NO_MERCHANT : IngestionResult.Outcome.RESOLVED;
    }
}
