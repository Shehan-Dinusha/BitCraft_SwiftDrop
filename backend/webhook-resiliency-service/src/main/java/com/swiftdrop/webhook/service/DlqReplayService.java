package com.swiftdrop.webhook.service;

import com.swiftdrop.webhook.domain.entity.DeadLetterWebhookRecord;
import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import com.swiftdrop.webhook.domain.repository.DeadLetterWebhookRecordRepository;
import com.swiftdrop.webhook.domain.repository.WebhookEndpointRepository;
import com.swiftdrop.webhook.dto.DeadLetterWebhookResponse;
import com.swiftdrop.webhook.dto.ReplayDlqResponse;
import com.swiftdrop.webhook.dto.WebhookDeliveryAttemptResponse;
import com.swiftdrop.webhook.exception.DlqRecordNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlqReplayService {

    private final DeadLetterWebhookRecordRepository dlqRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final OutboundWebhookDispatcher dispatcher;

    @Transactional(readOnly = true)
    public List<DeadLetterWebhookResponse> listDlqRecords(UUID merchantId, Boolean unreplayedOnly) {
        List<DeadLetterWebhookRecord> records;
        if (merchantId != null) {
            records = dlqRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
            if (Boolean.TRUE.equals(unreplayedOnly)) {
                records = records.stream().filter(r -> !r.isReplayed()).toList();
            }
        } else if (Boolean.TRUE.equals(unreplayedOnly)) {
            records = dlqRepository.findByReplayedFalseOrderByCreatedAtDesc();
        } else {
            records = dlqRepository.findAllByOrderByCreatedAtDesc();
        }

        return records.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public DeadLetterWebhookResponse getDlqRecord(UUID dlqId) {
        return dlqRepository.findByDlqId(dlqId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new DlqRecordNotFoundException("Dead-letter webhook record not found with ID: " + dlqId));
    }

    /**
     * Executes manual redelivery replay for a specific dead-letter queue record.
     */
    @Transactional
    public ReplayDlqResponse replayDlqRecord(UUID dlqId) {
        log.info("Initiating DLQ replay for record: {}", dlqId);
        DeadLetterWebhookRecord record = dlqRepository.findByDlqId(dlqId)
                .orElseThrow(() -> new DlqRecordNotFoundException("Dead-letter webhook record not found with ID: " + dlqId));

        Optional<WebhookEndpoint> endpointOpt = endpointRepository.findByEndpointId(record.getEndpointId());
        if (endpointOpt.isEmpty()) {
            log.warn("Cannot replay DLQ record {}: Target endpoint {} no longer exists", dlqId, record.getEndpointId());
            return new ReplayDlqResponse(dlqId, "FAILED", 404, "Target endpoint no longer exists", Instant.now());
        }

        WebhookEndpoint endpoint = endpointOpt.get();
        if (!endpoint.isActive()) {
            log.warn("Cannot replay DLQ record {}: Target endpoint {} is deactivated", dlqId, record.getEndpointId());
            return new ReplayDlqResponse(dlqId, "FAILED", 400, "Target webhook endpoint is currently deactivated", Instant.now());
        }

        WebhookDeliveryAttemptResponse attempt = dispatcher.dispatch(endpoint, record.getEventType(), record.getPayload());

        if (attempt != null && attempt.success()) {
            record.setReplayed(true);
            record.setReplayedAt(Instant.now());
            dlqRepository.save(record);
            log.info("DLQ record {} replayed successfully to {}", dlqId, endpoint.getTargetUrl());

            return new ReplayDlqResponse(
                    dlqId,
                    "SUCCESS",
                    attempt.httpStatusCode(),
                    "Webhook replayed and accepted successfully by endpoint",
                    record.getReplayedAt()
            );
        } else {
            int code = attempt != null ? attempt.httpStatusCode() : 500;
            String errorMsg = attempt != null && attempt.errorMessage() != null ? attempt.errorMessage() : "Dispatcher failure";
            log.warn("DLQ replay failed for record {}: {}", dlqId, errorMsg);

            return new ReplayDlqResponse(
                    dlqId,
                    "FAILED",
                    code,
                    "Replay attempt failed: " + errorMsg,
                    Instant.now()
            );
        }
    }

    /**
     * Replays all unreplayed DLQ records for a given merchant or all merchants.
     */
    @Transactional
    public List<ReplayDlqResponse> replayAllUnreplayed(UUID merchantId) {
        log.info("Batch replaying all unreplayed DLQ records (merchantId: {})", merchantId);
        List<DeadLetterWebhookRecord> candidates;
        if (merchantId != null) {
            candidates = dlqRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                    .filter(r -> !r.isReplayed())
                    .toList();
        } else {
            candidates = dlqRepository.findByReplayedFalseOrderByCreatedAtDesc();
        }

        List<ReplayDlqResponse> results = new ArrayList<>();
        for (DeadLetterWebhookRecord rec : candidates) {
            results.add(replayDlqRecord(rec.getDlqId()));
        }
        return results;
    }

    private DeadLetterWebhookResponse mapToResponse(DeadLetterWebhookRecord record) {
        return new DeadLetterWebhookResponse(
                record.getDlqId(),
                record.getEndpointId(),
                record.getMerchantId(),
                record.getEventType(),
                record.getPayload(),
                record.getFailureReason(),
                record.getTotalAttempts(),
                record.isReplayed(),
                record.getReplayedAt(),
                record.getCreatedAt()
        );
    }
}
