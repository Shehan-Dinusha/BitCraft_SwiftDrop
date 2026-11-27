package com.swiftdrop.webhook.controller;

import com.swiftdrop.contracts.dto.ApiErrorResponse;
import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.webhook.dto.DeadLetterWebhookResponse;
import com.swiftdrop.webhook.exception.LegacyReplayRetiredException;
import com.swiftdrop.webhook.service.DlqReplayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Legacy dead-letter records: read-only history. Replay was retired in Step 17 and answers 410 Gone without reading
 * or sending anything; failed deliveries are replayed through {@link FailedDeliveryController}.
 */
@RestController
@RequestMapping("/api/v1/webhooks/dlq")
@Tag(name = "DlqReplay", description = "Legacy dead-letter records (read-only history). Replay moved to the failed-delivery API.")
@RequiredArgsConstructor
public class DlqReplayController {

    private final DlqReplayService dlqReplayService;

    @GetMapping("/health")
    @Operation(summary = "Health check for the legacy dead-letter API")
    public ApiResponse<String> health() {
        return ApiResponse.success("DlqReplayController is ready", "Health check OK");
    }

    @GetMapping
    @Operation(summary = "List legacy dead-letter webhook records (read-only history)",
            description = "A MERCHANT sees only its own records (a different merchantId is refused with 403). "
                    + "A SYSTEM_ADMIN may filter by merchantId or omit it. Other roles: 403.")
    public ApiResponse<List<DeadLetterWebhookResponse>> listDlqRecords(
            @RequestParam(required = false) @Parameter(description = "Merchant filter; SYSTEM_ADMIN only")
            UUID merchantId,
            @RequestParam(required = false, defaultValue = "false") Boolean unreplayedOnly,
            CallerContext caller) {
        List<DeadLetterWebhookResponse> records = dlqReplayService.listDlqRecords(merchantId, unreplayedOnly, caller);
        return ApiResponse.success(records, "DLQ records retrieved");
    }

    @GetMapping("/{dlqId}")
    @Operation(summary = "Get a legacy dead-letter record by ID",
            description = "MERCHANT (own records) and SYSTEM_ADMIN. Another merchant's record returns 404, exactly like "
                    + "an unknown ID. Other roles: 403.")
    public ApiResponse<DeadLetterWebhookResponse> getDlqRecord(
            @PathVariable @Parameter(description = "DLQ record UUID") UUID dlqId, CallerContext caller) {
        return ApiResponse.success(dlqReplayService.getDlqRecord(dlqId, caller), "DLQ record retrieved");
    }

    @PostMapping("/{dlqId}/replay")
    @Operation(summary = "Retired: replay a legacy dead-letter record", deprecated = true,
            description = "Always 410 Gone for an identified caller; nothing is read or sent. Use "
                    + "POST /api/v1/webhooks/deliveries/failed/{deliveryId}/replay.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "Retired",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ApiResponse<Void> replayRecord(
            @PathVariable @Parameter(description = "DLQ record UUID") UUID dlqId, CallerContext caller) {
        throw new LegacyReplayRetiredException();
    }

    @PostMapping("/replay-all")
    @Operation(summary = "Retired: replay every legacy dead-letter record", deprecated = true,
            description = "Always 410 Gone for an identified caller; nothing is read or sent. Use "
                    + "POST /api/v1/webhooks/deliveries/failed/replay.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "Retired",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ApiResponse<Void> replayAllUnreplayed(
            @RequestParam(required = false) @Parameter(description = "Ignored") UUID merchantId,
            CallerContext caller) {
        throw new LegacyReplayRetiredException();
    }
}
