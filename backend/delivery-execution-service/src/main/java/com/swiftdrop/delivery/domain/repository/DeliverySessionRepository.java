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

    Page<DeliverySession> findByCurrentState(DeliveryState currentState, Pageable pageable);
}
