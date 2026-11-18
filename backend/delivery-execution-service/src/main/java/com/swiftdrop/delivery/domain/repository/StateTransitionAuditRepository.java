package com.swiftdrop.delivery.domain.repository;

import com.swiftdrop.delivery.domain.entity.StateTransitionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StateTransitionAuditRepository extends JpaRepository<StateTransitionAudit, Long> {

    List<StateTransitionAudit> findByDeliveryIdOrderByCreatedAtDesc(UUID deliveryId);

    List<StateTransitionAudit> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
