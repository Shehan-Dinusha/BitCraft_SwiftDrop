package com.swiftdrop.order.domain.repository;

import com.swiftdrop.order.domain.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    List<OrderStatusHistory> findByOrderIdOrderByOccurredAtAscIdAsc(UUID orderId);

    Optional<OrderStatusHistory> findFirstByOrderIdOrderByOccurredAtDescIdDesc(UUID orderId);
}
