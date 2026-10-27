package com.swiftdrop.webhook.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.webhook.dto.DeadLetterWebhookResponse;
import com.swiftdrop.webhook.dto.ReplayDlqResponse;
import com.swiftdrop.webhook.service.DlqReplayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/api/v1/webhooks/dlq", "/api/v1/dlq-replay"})
@Tag(name = "DlqReplay", description = "Dead-Letter Queue manual review, error diagnostics, and redelivery replay")
@RequiredArgsConstructor
public class DlqReplayController {

    private final DlqReplayService dlqReplayService;

    @GetMapping("/health")
    @Operation(summary = "Health check for DLQ replay controller")
    public ApiResponse<String> health() {
        return ApiResponse.success("DlqReplayController is ready", "Health check OK");
    }

    @GetMapping
    @Operation(summary = "List Dead-Letter Queue webhook records")
    public ApiResponse<List<DeadLetterWebhookResponse>> listDlqRecords(
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false, defaultValue = "false") Boolean unreplayedOnly,
            @RequestHeader(value = "X-Merchant-Id", required = false) String headerMerchantId) {

        UUID targetMerchant = merchantId;
        if (targetMerchant == null && headerMerchantId != null && !headerMerchantId.isBlank()) {
            try {
                targetMerchant = UUID.fromString(headerMerchantId);
            } catch (IllegalArgumentException ignored) {
            }
        }

        List<DeadLetterWebhookResponse> records = dlqReplayService.listDlqRecords(targetMerchant, unreplayedOnly);
        return ApiResponse.success(records, "DLQ records retrieved");
    }

    @GetMapping("/{dlqId}")
    @Operation(summary = "Get detailed DLQ record by ID")
    public ApiResponse<DeadLetterWebhookResponse> getDlqRecord(
            @PathVariable @Parameter(description = "DLQ record UUID") UUID dlqId) {
        DeadLetterWebhookResponse record = dlqReplayService.getDlqRecord(dlqId);
        return ApiResponse.success(record, "DLQ record retrieved");
    }

    @PostMapping("/{dlqId}/replay")
    @Operation(summary = "Manually replay a dead-lettered webhook record")
    public ApiResponse<ReplayDlqResponse> replayRecord(
            @PathVariable @Parameter(description = "DLQ record UUID") UUID dlqId) {
        log.info("Request received to manually replay DLQ record: {}", dlqId);
        ReplayDlqResponse response = dlqReplayService.replayDlqRecord(dlqId);
        return ApiResponse.success(response, "Replay executed");
    }

    @PostMapping("/replay-all")
    @Operation(summary = "Batch replay all unreplayed DLQ records")
    public ApiResponse<List<ReplayDlqResponse>> replayAllUnreplayed(
            @RequestParam(required = false) UUID merchantId,
            @RequestHeader(value = "X-Merchant-Id", required = false) String headerMerchantId) {

        UUID targetMerchant = merchantId;
        if (targetMerchant == null && headerMerchantId != null && !headerMerchantId.isBlank()) {
            try {
                targetMerchant = UUID.fromString(headerMerchantId);
            } catch (IllegalArgumentException ignored) {
            }
        }

        log.info("Request received to batch replay all unreplayed DLQ records (merchantId: {})", targetMerchant);
        List<ReplayDlqResponse> response = dlqReplayService.replayAllUnreplayed(targetMerchant);
        return ApiResponse.success(response, "Batch replay execution finished");
    }
}
