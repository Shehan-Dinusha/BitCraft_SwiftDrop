package com.swiftdrop.order.service;

import com.swiftdrop.order.domain.entity.CustomerOrder;
import com.swiftdrop.order.domain.entity.OrderStatusHistory;
import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import com.swiftdrop.order.domain.repository.OrderStatusHistoryRepository;
import com.swiftdrop.order.dto.InternalTrackingView;
import com.swiftdrop.order.exception.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InternalTrackingService {

    private final CustomerOrderRepository customerOrderRepository;
    private final OrderStatusHistoryRepository historyRepository;

    @Transactional(readOnly = true)
    public InternalTrackingView getTrackingView(String trackingNumber) {
        CustomerOrder order = customerOrderRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));

        List<InternalTrackingView.HistoryEntry> history = historyRepository
                .findByOrderIdOrderByOccurredAtAscIdAsc(order.getOrderId()).stream()
                .sorted(Comparator.comparing(OrderStatusHistory::getOccurredAt)
                        .thenComparing(OrderStatusHistory::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(row -> new InternalTrackingView.HistoryEntry(row.getStatus(), row.getOccurredAt()))
                .toList();

        return new InternalTrackingView(
                order.getTrackingNumber(),
                order.getStatus(),
                history,
                order.getPickupCity(),
                order.getPickupDistrict(),
                order.getDeliveryCity(),
                order.getDeliveryDistrict());
    }
}
