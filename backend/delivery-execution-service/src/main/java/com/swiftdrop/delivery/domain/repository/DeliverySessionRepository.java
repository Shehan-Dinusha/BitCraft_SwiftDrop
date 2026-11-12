package com.swiftdrop.delivery.domain.repository;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliverySessionRepository extends JpaRepository<DeliverySession, Long> {

    Optional<DeliverySession> findByDeliveryId(UUID deliveryId);

    Optional<DeliverySession> findByOrderId(UUID orderId);

    Optional<DeliverySession> findByTrackingNumber(String trackingNumber);

    List<DeliverySession> findByCourierId(UUID courierId);

    List<DeliverySession> findByCourierIdAndCurrentState(UUID courierId, DeliveryState currentState);

    List<DeliverySession> findByCourierIdOrderByCreatedAtAsc(UUID courierId);

    @org.springframework.data.jpa.repository.Query("SELECT s FROM DeliverySession s WHERE s.courierId = :courierId AND ((s.createdAt >= :startOfDay AND s.createdAt < :endOfDay) OR (s.updatedAt >= :startOfDay AND s.updatedAt < :endOfDay)) ORDER BY s.createdAt ASC")
    List<DeliverySession> findByCourierIdAndDateRange(
            @org.springframework.data.repository.query.Param("courierId") UUID courierId,
            @org.springframework.data.repository.query.Param("startOfDay") java.time.Instant startOfDay,
            @org.springframework.data.repository.query.Param("endOfDay") java.time.Instant endOfDay);

    Page<DeliverySession> findByCurrentState(DeliveryState currentState, Pageable pageable);
}
