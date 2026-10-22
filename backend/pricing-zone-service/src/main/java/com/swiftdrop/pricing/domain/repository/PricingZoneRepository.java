package com.swiftdrop.pricing.domain.repository;

import com.swiftdrop.pricing.domain.entity.PricingZone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingZoneRepository extends JpaRepository<PricingZone, Long> {
}
