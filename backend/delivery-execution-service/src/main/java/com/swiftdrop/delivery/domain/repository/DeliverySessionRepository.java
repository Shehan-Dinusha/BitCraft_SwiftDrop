package com.swiftdrop.delivery.domain.repository;

import com.swiftdrop.delivery.domain.entity.DeliverySession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliverySessionRepository extends JpaRepository<DeliverySession, Long> {
}
