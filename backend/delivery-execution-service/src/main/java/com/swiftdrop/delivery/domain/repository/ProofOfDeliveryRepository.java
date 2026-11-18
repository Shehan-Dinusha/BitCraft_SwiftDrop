package com.swiftdrop.delivery.domain.repository;

import com.swiftdrop.delivery.domain.entity.ProofOfDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProofOfDeliveryRepository extends JpaRepository<ProofOfDelivery, Long> {
    Optional<ProofOfDelivery> findTopByDeliveryIdOrderByCreatedAtDesc(UUID deliveryId);
}
