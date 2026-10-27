package com.swiftdrop.pricing.domain.repository;

import com.swiftdrop.pricing.domain.entity.DistrictZoneMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DistrictZoneMappingRepository extends JpaRepository<DistrictZoneMapping, String> {
    Optional<DistrictZoneMapping> findByDistrict(String district);
}
