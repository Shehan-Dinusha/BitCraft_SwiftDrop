package com.swiftdrop.delivery.domain.repository;

import com.swiftdrop.delivery.domain.entity.TrackingBreadcrumb;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrackingBreadcrumbRepository extends JpaRepository<TrackingBreadcrumb, Long> {

    List<TrackingBreadcrumb> findByDeliveryIdOrderByRecordedAtDesc(UUID deliveryId);

    List<TrackingBreadcrumb> findTop50ByDeliveryIdOrderByRecordedAtDesc(UUID deliveryId);
}
