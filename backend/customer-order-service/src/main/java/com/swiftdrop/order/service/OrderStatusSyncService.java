package com.swiftdrop.order.service;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import com.swiftdrop.order.domain.entity.OrderStatusHistory;
import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import com.swiftdrop.order.domain.repository.OrderStatusHistoryRepository;
import com.swiftdrop.order.domain.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps an order's status in step with {@code parcel.status.changed} events from delivery and dispatch.
 *
 * <p>The event id is claimed first, in the same transaction as the update, so a message delivered twice is
 * applied once. Events that do not apply are skipped, never thrown: a skipped message is acknowledged
 * instead of being redelivered forever. Only genuine failures (for example the database being down) throw,
 * so the listener's retry and dead-letter handling takes over. Logs carry ids only, never addresses,
 * phones or names.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStatusSyncService {

    /**
     * Statuses an order does not leave. FAILED is deliberately not here: a failed delivery is retried
     * (up to three attempts) and so moves on to out-for-delivery or returned-to-sender.
     */
    private static final Set<PublicOrderStatus> FINAL_STATUSES = Set.of(
            PublicOrderStatus.DELIVERED, PublicOrderStatus.CANCELLED, PublicOrderStatus.RETURNED_TO_SENDER);

    public enum Outcome {
        APPLIED,
        SUB_STATUS_UPDATED,
        DUPLICATE_EVENT,
        UNKNOWN_ORDER,
        NO_STATUS,
        SAME_STATUS,
        FINAL_STATUS,
        OUT_OF_ORDER
    }

    private final CustomerOrderRepository customerOrderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderStatusHistoryService orderStatusHistoryService;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public Outcome apply(ParcelStatusChangedEvent event) {
        if (processedEventRepository.claim(event.eventId()) == 0) {
            log.debug("Skipping duplicate event eventId={}", event.eventId());
            return Outcome.DUPLICATE_EVENT;
        }
        if (event.newPublicStatus() == null) {
            log.debug("Skipping event without a public status eventId={}", event.eventId());
            return Outcome.NO_STATUS;
        }

        Optional<CustomerOrder> found = findOrder(event);
        if (found.isEmpty()) {
            log.warn("No order for status event eventId={}, orderId={}, trackingNumber={}, correlationId={}",
                    event.eventId(), event.orderId(), event.trackingNumber(), event.correlationId());
            return Outcome.UNKNOWN_ORDER;
        }
        CustomerOrder order = found.get();

        Optional<OrderStatusHistory> latest = historyRepository.findFirstByOrderIdOrderByOccurredAtDescIdDesc(order.getOrderId());
        if (latest.isPresent() && event.timestamp().isBefore(latest.get().getOccurredAt())) {
            log.info("Skipping out-of-order event eventId={}, orderId={}, correlationId={}",
                    event.eventId(), order.getOrderId(), event.correlationId());
            return Outcome.OUT_OF_ORDER;
        }

        if (event.newPublicStatus() == order.getStatus()) {
            // Includes the events this service publishes itself on create and cancel, which stops update loops.
            if (event.internalSubStatus() != null && event.internalSubStatus() != order.getInternalQueueStatus()) {
                order.setInternalQueueStatus(event.internalSubStatus());
                order.setUpdatedAt(Instant.now());
                customerOrderRepository.save(order);
                return Outcome.SUB_STATUS_UPDATED;
            }
            return Outcome.SAME_STATUS;
        }

        if (FINAL_STATUSES.contains(order.getStatus())) {
            log.warn("Ignoring status change out of final status {}: eventId={}, orderId={}, correlationId={}",
                    order.getStatus(), event.eventId(), order.getOrderId(), event.correlationId());
            return Outcome.FINAL_STATUS;
        }

        order.setStatus(event.newPublicStatus());
        if (event.internalSubStatus() != null) {
            order.setInternalQueueStatus(event.internalSubStatus());
        }
        if (event.newPublicStatus() == PublicOrderStatus.CANCELLED && order.getCancelledAt() == null) {
            order.setCancelledAt(event.timestamp());
        }
        order.setUpdatedAt(Instant.now());
        CustomerOrder saved = customerOrderRepository.save(order);
        orderStatusHistoryService.recordStatus(saved.getOrderId(), saved.getStatus(), saved.getInternalQueueStatus(),
                event.eventId(), event.timestamp());

        log.info("Order status synced: orderId={}, {} -> {}, eventId={}, correlationId={}",
                saved.getOrderId(), event.previousPublicStatus(), saved.getStatus(), event.eventId(), event.correlationId());
        return Outcome.APPLIED;
    }

    private Optional<CustomerOrder> findOrder(ParcelStatusChangedEvent event) {
        Optional<CustomerOrder> order = event.orderId() != null
                ? customerOrderRepository.findByOrderId(event.orderId())
                : Optional.empty();
        if (order.isEmpty() && event.trackingNumber() != null) {
            order = customerOrderRepository.findByTrackingNumber(event.trackingNumber());
        }
        return order;
    }
}
