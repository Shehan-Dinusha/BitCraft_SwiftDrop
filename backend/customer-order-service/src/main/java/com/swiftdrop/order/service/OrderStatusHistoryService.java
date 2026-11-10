package com.swiftdrop.order.service;

import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import com.swiftdrop.order.domain.entity.OrderStatusHistory;
import com.swiftdrop.order.domain.repository.OrderStatusHistoryRepository;
import com.swiftdrop.order.dto.OrderStatusHistoryEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Single place that writes order status history rows, so create, cancel and event sync stay consistent. */
@Service
@RequiredArgsConstructor
public class OrderStatusHistoryService {

    private final OrderStatusHistoryRepository historyRepository;

    /** The statuses the order has been through, oldest first. */
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryEntry> listForOrder(UUID orderId) {
        return historyRepository.findByOrderIdOrderByOccurredAtAscIdAsc(orderId).stream()
                .map(row -> new OrderStatusHistoryEntry(row.getStatus(), row.getOccurredAt()))
                .toList();
    }

    /** Records the order's current status at its last update time, as part of the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OrderStatusHistory recordCurrentStatus(CustomerOrder order, UUID eventId) {
        return recordStatus(order.getOrderId(), order.getStatus(), order.getInternalQueueStatus(), eventId, order.getUpdatedAt());
    }

    /** Records a status that was reached at {@code occurredAt}, as part of the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OrderStatusHistory recordStatus(UUID orderId, PublicOrderStatus status, InternalQueueStatus internalSubStatus,
                                           UUID eventId, Instant occurredAt) {
        return historyRepository.save(OrderStatusHistory.builder()
                .orderId(orderId)
                .status(status)
                .internalSubStatus(internalSubStatus)
                .eventId(eventId)
                .occurredAt(occurredAt)
                .build());
    }
}
