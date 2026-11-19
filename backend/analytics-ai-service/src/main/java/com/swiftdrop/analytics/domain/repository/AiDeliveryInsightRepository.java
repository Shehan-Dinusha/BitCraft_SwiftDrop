package com.swiftdrop.analytics.domain.repository;

import com.swiftdrop.analytics.domain.entity.AiDeliveryInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiDeliveryInsightRepository extends JpaRepository<AiDeliveryInsight, Long> {

    List<AiDeliveryInsight> findByTrackingNumber(String trackingNumber);

    List<AiDeliveryInsight> findTop20ByOrderByCreatedAtDesc();
}
