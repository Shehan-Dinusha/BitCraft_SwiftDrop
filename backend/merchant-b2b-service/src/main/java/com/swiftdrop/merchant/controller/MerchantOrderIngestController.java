package com.swiftdrop.merchant.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.merchant.dto.BulkBatchItemResponse;
import com.swiftdrop.merchant.dto.BulkIngestBatchResponse;
import com.swiftdrop.merchant.dto.BulkIngestRequest;
import com.swiftdrop.merchant.dto.BulkOrderIngestItem;
import com.swiftdrop.merchant.service.BulkCsvOrderProcessor;
import com.swiftdrop.merchant.service.BulkOrderIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "MerchantOrderIngest", description = "B2B bulk and CSV delivery order ingestion API")
public class MerchantOrderIngestController {

    private final BulkOrderIngestionService ingestionService;
    private final BulkCsvOrderProcessor csvProcessor;

    @GetMapping("/api/v1/merchant-order-ingest/health")
    @Operation(summary = "Check order ingestion controller health")
    public ApiResponse<String> health() {
        return ApiResponse.success("MerchantOrderIngestController is active");
    }

    @PostMapping("/api/v1/merchants/{merchantId}/ingest/bulk")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Ingest a batch of delivery orders via JSON payload")
    public ApiResponse<BulkIngestBatchResponse> ingestBulkOrders(
            @PathVariable UUID merchantId,
            @Valid @RequestBody BulkIngestRequest request) {
        log.info("Received bulk order ingestion request for merchant: {}", merchantId);
        BulkIngestBatchResponse batch = ingestionService.processBulkIngest(merchantId, request);
        return ApiResponse.success(batch, "Bulk order ingestion batch processed");
    }

    @PostMapping(value = "/api/v1/merchants/{merchantId}/ingest/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Upload and process bulk delivery orders from a CSV file")
    public ApiResponse<BulkIngestBatchResponse> ingestCsvOrders(
            @PathVariable UUID merchantId,
            @RequestParam("file") MultipartFile file) {
        log.info("Received CSV order ingestion upload for merchant: {}, file: {}", merchantId, file.getOriginalFilename());
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded CSV file cannot be empty");
        }

        try (InputStream is = file.getInputStream()) {
            List<BulkOrderIngestItem> items = csvProcessor.parseCsv(is);
            BulkIngestRequest request = new BulkIngestRequest(file.getOriginalFilename(), items);
            BulkIngestBatchResponse batch = ingestionService.processBulkIngest(merchantId, request);
            return ApiResponse.success(batch, "CSV orders processed successfully");
        } catch (Exception e) {
            log.error("Failed to process CSV file upload for merchant: {}", merchantId, e);
            throw new IllegalArgumentException("Failed to process CSV upload: " + e.getMessage(), e);
        }
    }

    @GetMapping("/api/v1/merchants/{merchantId}/batches")
    @Operation(summary = "List ingestion batches for a merchant")
    public ApiResponse<List<BulkIngestBatchResponse>> listBatches(@PathVariable UUID merchantId) {
        List<BulkIngestBatchResponse> batches = ingestionService.listBatches(merchantId);
        return ApiResponse.success(batches, "Ingestion batches retrieved");
    }

    @GetMapping("/api/v1/merchants/batches/{batchId}")
    @Operation(summary = "Get ingestion batch summary by UUID")
    public ApiResponse<BulkIngestBatchResponse> getBatch(@PathVariable UUID batchId) {
        BulkIngestBatchResponse batch = ingestionService.getBatchById(batchId);
        return ApiResponse.success(batch, "Batch retrieved");
    }

    @GetMapping("/api/v1/merchants/batches/{batchId}/items")
    @Operation(summary = "Get row-level parcel tracking details for a batch")
    public ApiResponse<List<BulkBatchItemResponse>> getBatchItems(@PathVariable UUID batchId) {
        List<BulkBatchItemResponse> items = ingestionService.getBatchItems(batchId);
        return ApiResponse.success(items, "Batch items retrieved");
    }
}
