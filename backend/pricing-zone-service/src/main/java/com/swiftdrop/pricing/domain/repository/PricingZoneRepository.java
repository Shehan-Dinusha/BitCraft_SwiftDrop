package com.swiftdrop.pricing.domain.repository;

import com.swiftdrop.pricing.domain.entity.PricingZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PricingZoneRepository extends JpaRepository<PricingZone, Long> {
    Optional<PricingZone> findByZoneCode(String zoneCode);
}
