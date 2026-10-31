package com.swiftdrop.merchant.domain.repository;

import com.swiftdrop.merchant.domain.entity.BulkIngestBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BulkIngestBatchRepository extends JpaRepository<BulkIngestBatch, Long> {
    Optional<BulkIngestBatch> findByBatchId(UUID batchId);
    List<BulkIngestBatch> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
