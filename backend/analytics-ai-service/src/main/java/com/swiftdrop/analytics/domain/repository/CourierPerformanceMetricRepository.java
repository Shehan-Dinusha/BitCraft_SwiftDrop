package com.swiftdrop.analytics.domain.repository;

import com.swiftdrop.analytics.domain.entity.CourierPerformanceMetric;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourierPerformanceMetricRepository extends JpaRepository<CourierPerformanceMetric, Long> {

    Optional<CourierPerformanceMetric> findByCourierId(UUID courierId);

    List<CourierPerformanceMetric> findAllByCurrentDistrict(SriLankanDistrict currentDistrict);
}
