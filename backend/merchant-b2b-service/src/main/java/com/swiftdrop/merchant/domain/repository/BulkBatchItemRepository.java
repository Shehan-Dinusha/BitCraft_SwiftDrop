package com.swiftdrop.merchant.domain.repository;

import com.swiftdrop.merchant.domain.entity.BulkBatchItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BulkBatchItemRepository extends JpaRepository<BulkBatchItem, Long> {
    List<BulkBatchItem> findByBatchIdOrderByRowIndexAsc(UUID batchId);
    Optional<BulkBatchItem> findByOrderId(UUID orderId);
    Optional<BulkBatchItem> findByTrackingNumber(String trackingNumber);
}
