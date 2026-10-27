package com.swiftdrop.merchant.service;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import com.swiftdrop.merchant.domain.entity.BulkBatchItem;
import com.swiftdrop.merchant.domain.entity.BulkIngestBatch;
import com.swiftdrop.merchant.domain.entity.MerchantAccount;
import com.swiftdrop.merchant.domain.repository.BulkBatchItemRepository;
import com.swiftdrop.merchant.domain.repository.BulkIngestBatchRepository;
import com.swiftdrop.merchant.domain.repository.MerchantAccountRepository;
import com.swiftdrop.merchant.dto.*;
import com.swiftdrop.merchant.exception.BatchNotFoundException;
import com.swiftdrop.merchant.messaging.publisher.MerchantEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkOrderIngestionService {

    private final BulkIngestBatchRepository batchRepository;
    private final BulkBatchItemRepository batchItemRepository;
    private final MerchantAccountRepository merchantAccountRepository;
    private final MerchantEventPublisher eventPublisher;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public BulkIngestBatchResponse processBulkIngest(UUID merchantId, BulkIngestRequest request) {
        log.info("Processing bulk order ingest for merchant: {}, total orders: {}",
                merchantId, request.orders().size());

        // Lookup or resolve merchant's operating district
        SriLankanDistrict pickupDistrict = merchantAccountRepository.findByMerchantId(merchantId)
                .map(MerchantAccount::getOperatingDistrict)
                .orElse(SriLankanDistrict.COLOMBO);

        UUID batchId = UUID.randomUUID();
        BulkIngestBatch batch = BulkIngestBatch.builder()
                .batchId(batchId)
                .merchantId(merchantId)
                .fileName(request.fileName() != null ? request.fileName() : "batch_" + batchId.toString().substring(0, 8) + ".csv")
                .batchStatus("PROCESSING")
                .totalRecords(request.orders().size())
                .successCount(0)
                .failedCount(0)
                .build();

        batch = batchRepository.save(batch);

        int successCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();
        int rowIndex = 1;

        for (BulkOrderIngestItem item : request.orders()) {
            UUID orderId = UUID.randomUUID();
            String trackingNumber = generateTrackingNumber(pickupDistrict);

            try {
                // Determine order speed
                OrderSpeed speed = OrderSpeed.STANDARD;
                if (item.deliverySpeed() != null && item.deliverySpeed().equalsIgnoreCase("EXPRESS")) {
                    speed = OrderSpeed.EXPRESS;
                }

                BulkBatchItem batchItem = BulkBatchItem.builder()
                        .batchId(batchId)
                        .rowIndex(rowIndex)
                        .orderId(orderId)
                        .trackingNumber(trackingNumber)
                        .recipientName(item.recipientName())
                        .recipientPhone(item.recipientPhone())
                        .deliveryDistrict(item.deliveryDistrict() != null ? item.deliveryDistrict() : SriLankanDistrict.COLOMBO)
                        .deliveryAddress(item.deliveryAddress())
                        .packageWeightKg(item.packageWeightKg() != null ? item.packageWeightKg() : BigDecimal.valueOf(1.0))
                        .deliverySpeed(speed.name())
                        .itemStatus("SUCCESS")
                        .build();

                batchItemRepository.save(batchItem);

                // Publish OrderCreatedEvent onto swiftdrop.events.exchange
                BigDecimal estimatedPrice = speed == OrderSpeed.EXPRESS ? BigDecimal.valueOf(750.0) : BigDecimal.valueOf(450.0);
                OrderCreatedEvent event = new OrderCreatedEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        orderId,
                        trackingNumber,
                        merchantId,
                        merchantId,
                        pickupDistrict,
                        item.deliveryDistrict() != null ? item.deliveryDistrict() : SriLankanDistrict.COLOMBO,
                        speed,
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(5000.0),
                        estimatedPrice,
                        "LKR",
                        Instant.now()
                );
                eventPublisher.publishOrderCreated(event);

                successCount++;
            } catch (Exception e) {
                log.error("Failed to process row {} in batch {}", rowIndex, batchId, e);
                failedCount++;
                errors.add("Row " + rowIndex + ": " + e.getMessage());

                BulkBatchItem failedItem = BulkBatchItem.builder()
                        .batchId(batchId)
                        .rowIndex(rowIndex)
                        .recipientName(item.recipientName() != null ? item.recipientName() : "Unknown")
                        .recipientPhone(item.recipientPhone() != null ? item.recipientPhone() : "N/A")
                        .deliveryDistrict(item.deliveryDistrict() != null ? item.deliveryDistrict() : SriLankanDistrict.COLOMBO)
                        .deliveryAddress(item.deliveryAddress() != null ? item.deliveryAddress() : "N/A")
                        .itemStatus("FAILED")
                        .errorMessage(e.getMessage())
                        .build();
                batchItemRepository.save(failedItem);
            }
            rowIndex++;
        }

        batch.setSuccessCount(successCount);
        batch.setFailedCount(failedCount);
        batch.setBatchStatus(successCount > 0 ? "COMPLETED" : "FAILED");
        batch.setCompletedAt(Instant.now());
        if (!errors.isEmpty()) {
            batch.setErrorSummary(String.join("; ", errors));
        }

        BulkIngestBatch finalBatch = batchRepository.save(batch);
        return mapToBatchResponse(finalBatch);
    }

    @Transactional(readOnly = true)
    public BulkIngestBatchResponse getBatchById(UUID batchId) {
        BulkIngestBatch batch = batchRepository.findByBatchId(batchId)
                .orElseThrow(() -> new BatchNotFoundException("Bulk ingest batch not found: " + batchId));
        return mapToBatchResponse(batch);
    }

    @Transactional(readOnly = true)
    public List<BulkIngestBatchResponse> listBatches(UUID merchantId) {
        return batchRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .map(this::mapToBatchResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BulkBatchItemResponse> getBatchItems(UUID batchId) {
        return batchItemRepository.findByBatchIdOrderByRowIndexAsc(batchId).stream()
                .map(this::mapToItemResponse)
                .toList();
    }

    private String generateTrackingNumber(SriLankanDistrict district) {
        String distPrefix = district.name().substring(0, Math.min(3, district.name().length()));
        String dateStr = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int rand = 1000 + secureRandom.nextInt(9000);
        return "SD-" + distPrefix + "-" + dateStr + "-" + rand;
    }

    private BulkIngestBatchResponse mapToBatchResponse(BulkIngestBatch batch) {
        return new BulkIngestBatchResponse(
                batch.getBatchId(),
                batch.getMerchantId(),
                batch.getFileName(),
                batch.getBatchStatus(),
                batch.getTotalRecords(),
                batch.getSuccessCount(),
                batch.getFailedCount(),
                batch.getErrorSummary(),
                batch.getCreatedAt(),
                batch.getCompletedAt()
        );
    }

    private BulkBatchItemResponse mapToItemResponse(BulkBatchItem item) {
        return new BulkBatchItemResponse(
                item.getBatchId(),
                item.getRowIndex(),
                item.getTrackingNumber(),
                item.getOrderId(),
                item.getRecipientName(),
                item.getRecipientPhone(),
                item.getDeliveryDistrict(),
                item.getDeliveryAddress(),
                item.getPackageWeightKg(),
                item.getDeliverySpeed(),
                item.getItemStatus(),
                item.getErrorMessage(),
                item.getCreatedAt()
        );
    }
}
