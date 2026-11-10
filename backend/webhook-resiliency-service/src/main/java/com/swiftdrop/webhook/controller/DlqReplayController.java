package com.swiftdrop.webhook.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.security.CallerContext;
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
    @Operation(summary = "List Dead-Letter Queue webhook records",
            description = "A MERCHANT sees only its own records (a different merchantId is refused with 403). "
                    + "SYSTEM_ADMIN and DISPATCHER may filter by merchantId or omit it; DISPATCHER responses omit the payload.")
    public ApiResponse<List<DeadLetterWebhookResponse>> listDlqRecords(
            @RequestParam(required = false) @Parameter(description = "Merchant filter; SYSTEM_ADMIN and DISPATCHER only")
            UUID merchantId,
            @RequestParam(required = false, defaultValue = "false") Boolean unreplayedOnly,
            CallerContext caller) {
        List<DeadLetterWebhookResponse> records = dlqReplayService.listDlqRecords(merchantId, unreplayedOnly, caller);
        return ApiResponse.success(records, "DLQ records retrieved");
    }

    @GetMapping("/{dlqId}")
    @Operation(summary = "Get detailed DLQ record by ID",
            description = "Another merchant's record returns 404, exactly like an unknown ID. DISPATCHER responses omit the payload.")
    public ApiResponse<DeadLetterWebhookResponse> getDlqRecord(
            @PathVariable @Parameter(description = "DLQ record UUID") UUID dlqId, CallerContext caller) {
        DeadLetterWebhookResponse record = dlqReplayService.getDlqRecord(dlqId, caller);
        return ApiResponse.success(record, "DLQ record retrieved");
    }

    @PostMapping("/{dlqId}/replay")
    @Operation(summary = "Manually replay a dead-lettered webhook record",
            description = "MERCHANT (own records) and SYSTEM_ADMIN only.")
    public ApiResponse<ReplayDlqResponse> replayRecord(
            @PathVariable @Parameter(description = "DLQ record UUID") UUID dlqId, CallerContext caller) {
        log.info("Request received to manually replay DLQ record: {}", dlqId);
        ReplayDlqResponse response = dlqReplayService.replayDlqRecord(dlqId, caller);
        return ApiResponse.success(response, "Replay executed");
    }

    @PostMapping("/replay-all")
    @Operation(summary = "Batch replay all unreplayed DLQ records",
            description = "A MERCHANT replays its own records. A SYSTEM_ADMIN may target one merchant or, without "
                    + "merchantId, every merchant. Other roles: 403.")
    public ApiResponse<List<ReplayDlqResponse>> replayAllUnreplayed(
            @RequestParam(required = false) @Parameter(description = "Merchant filter; SYSTEM_ADMIN only")
            UUID merchantId,
            CallerContext caller) {
        log.info("Request received to batch replay all unreplayed DLQ records (merchantId filter: {})", merchantId);
        List<ReplayDlqResponse> response = dlqReplayService.replayAllUnreplayed(merchantId, caller);
        return ApiResponse.success(response, "Batch replay execution finished");
    }
}
