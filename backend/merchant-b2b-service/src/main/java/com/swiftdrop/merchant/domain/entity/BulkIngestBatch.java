package com.swiftdrop.merchant.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bulk_ingest_batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkIngestBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, unique = true, updatable = false)
    private UUID batchId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "batch_status", nullable = false, length = 64)
    @Builder.Default
    private String batchStatus = "PENDING";

    @Column(name = "total_records", nullable = false)
    @Builder.Default
    private int totalRecords = 0;

    @Column(name = "success_count", nullable = false)
    @Builder.Default
    private int successCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private int failedCount = 0;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    public void prePersist() {
        if (this.batchId == null) {
            this.batchId = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.batchStatus == null) {
            this.batchStatus = "PENDING";
        }
    }
}
