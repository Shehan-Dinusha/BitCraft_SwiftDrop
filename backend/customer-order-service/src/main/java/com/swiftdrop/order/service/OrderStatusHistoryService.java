package com.swiftdrop.order.service;

import com.swiftdrop.order.domain.entity.CustomerOrder;
import com.swiftdrop.order.domain.entity.OrderStatusHistory;
import com.swiftdrop.order.domain.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Single place that writes order status history rows, so create, cancel and event sync stay consistent. */
@Service
@RequiredArgsConstructor
public class OrderStatusHistoryService {

    private final OrderStatusHistoryRepository historyRepository;

    /** Records the order's current status at its last update time, as part of the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OrderStatusHistory recordCurrentStatus(CustomerOrder order, UUID eventId) {
        return historyRepository.save(OrderStatusHistory.builder()
                .orderId(order.getOrderId())
                .status(order.getStatus())
                .internalSubStatus(order.getInternalQueueStatus())
                .eventId(eventId)
                .occurredAt(order.getUpdatedAt())
                .build());
    }
}
