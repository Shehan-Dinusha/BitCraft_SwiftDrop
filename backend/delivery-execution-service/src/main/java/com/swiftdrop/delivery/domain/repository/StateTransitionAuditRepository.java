package com.swiftdrop.delivery.domain.repository;

import com.swiftdrop.delivery.domain.entity.StateTransitionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StateTransitionAuditRepository extends JpaRepository<StateTransitionAudit, Long> {

    List<StateTransitionAudit> findByDeliveryIdOrderByCreatedAtDesc(UUID deliveryId);

    List<StateTransitionAudit> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
