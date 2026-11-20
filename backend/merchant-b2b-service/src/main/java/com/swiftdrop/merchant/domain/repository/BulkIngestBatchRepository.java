package com.swiftdrop.merchant.domain.repository;

import com.swiftdrop.merchant.domain.entity.BulkIngestBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BulkIngestBatchRepository extends JpaRepository<BulkIngestBatch, Long> {
    Optional<BulkIngestBatch> findByBatchId(UUID batchId);
    List<BulkIngestBatch> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
