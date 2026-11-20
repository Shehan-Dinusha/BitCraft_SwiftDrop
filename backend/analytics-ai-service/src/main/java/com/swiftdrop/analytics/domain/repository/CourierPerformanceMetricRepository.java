package com.swiftdrop.analytics.domain.repository;

import com.swiftdrop.analytics.domain.entity.CourierPerformanceMetric;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierPerformanceMetricRepository extends JpaRepository<CourierPerformanceMetric, Long> {

    Optional<CourierPerformanceMetric> findByCourierId(UUID courierId);

    List<CourierPerformanceMetric> findAllByCurrentDistrict(SriLankanDistrict currentDistrict);
}
