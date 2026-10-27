package com.swiftdrop.analytics.domain.repository;

import com.swiftdrop.analytics.domain.entity.AiDeliveryInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiDeliveryInsightRepository extends JpaRepository<AiDeliveryInsight, Long> {

    List<AiDeliveryInsight> findByTrackingNumber(String trackingNumber);

    List<AiDeliveryInsight> findTop20ByOrderByCreatedAtDesc();
}
