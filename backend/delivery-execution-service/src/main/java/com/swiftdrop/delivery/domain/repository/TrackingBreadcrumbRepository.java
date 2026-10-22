package com.swiftdrop.delivery.domain.repository;

import com.swiftdrop.delivery.domain.entity.TrackingBreadcrumb;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackingBreadcrumbRepository extends JpaRepository<TrackingBreadcrumb, Long> {
}
