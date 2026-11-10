package com.swiftdrop.webhook.service;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.webhook.domain.entity.DeadLetterWebhookRecord;
import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import com.swiftdrop.webhook.domain.repository.DeadLetterWebhookRecordRepository;
import com.swiftdrop.webhook.domain.repository.WebhookEndpointRepository;
import com.swiftdrop.webhook.dto.DeadLetterWebhookResponse;
import com.swiftdrop.webhook.dto.ReplayDlqResponse;
import com.swiftdrop.webhook.dto.WebhookDeliveryAttemptResponse;
import com.swiftdrop.webhook.exception.DlqRecordNotFoundException;
import com.swiftdrop.webhook.service.access.MerchantAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy dead-letter records: listing, inspection and synchronous replay.
 *
 * <p>Access policy:</p>
 * <ul>
 *   <li>{@code MERCHANT}: list, view and replay its own records only.</li>
 *   <li>{@code SYSTEM_ADMIN}: every operation for every merchant.</li>
 *   <li>{@code DISPATCHER}: list and view across merchants, read-only, with the event payload withheld.</li>
 * </ul>
 * Another merchant's record is reported as not found, indistinguishable from a missing one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlqReplayService {

    private static final UserRole[] READERS = {UserRole.MERCHANT, UserRole.SYSTEM_ADMIN, UserRole.DISPATCHER};
    private static final UserRole[] REPLAYERS = {UserRole.MERCHANT, UserRole.SYSTEM_ADMIN};

    private final DeadLetterWebhookRecordRepository dlqRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final OutboundWebhookDispatcher dispatcher;

    @Transactional(readOnly = true)
    public List<DeadLetterWebhookResponse> listDlqRecords(UUID requestedMerchantId, Boolean unreplayedOnly,
                                                          CallerContext caller) {
        MerchantAccess.requireRole(caller, READERS);
        UUID merchantId = MerchantAccess.listScope(caller, requestedMerchantId,
                UserRole.SYSTEM_ADMIN, UserRole.DISPATCHER);
        List<DeadLetterWebhookRecord> records = findRecords(merchantId, Boolean.TRUE.equals(unreplayedOnly));
        return records.stream().map(record -> mapToResponse(record, caller)).toList();
    }

    @Transactional(readOnly = true)
    public DeadLetterWebhookResponse getDlqRecord(UUID dlqId, CallerContext caller) {
        MerchantAccess.requireRole(caller, READERS);
        DeadLetterWebhookRecord record = dlqRepository.findByDlqId(dlqId)
                .filter(found -> caller.role() == UserRole.DISPATCHER
                        || MerchantAccess.canAccess(caller, found.getMerchantId()))
                .orElseThrow(() -> notFound(dlqId));
        return mapToResponse(record, caller);
    }

    /**
     * Executes manual redelivery replay for a specific dead-letter queue record.
     */
    @Transactional
    public ReplayDlqResponse replayDlqRecord(UUID dlqId, CallerContext caller) {
        MerchantAccess.requireRole(caller, REPLAYERS);
        DeadLetterWebhookRecord record = dlqRepository.findByDlqId(dlqId)
                .filter(found -> MerchantAccess.canAccess(caller, found.getMerchantId()))
                .orElseThrow(() -> notFound(dlqId));
        return replay(record);
    }

    /**
     * Replays all unreplayed DLQ records: a merchant's own, or for an administrator one merchant's or everyone's.
     */
    @Transactional
    public List<ReplayDlqResponse> replayAllUnreplayed(UUID requestedMerchantId, CallerContext caller) {
        MerchantAccess.requireRole(caller, REPLAYERS);
        UUID merchantId = MerchantAccess.listScope(caller, requestedMerchantId, UserRole.SYSTEM_ADMIN);
        log.info("Batch replaying all unreplayed DLQ records (merchantId: {})", merchantId);

        List<ReplayDlqResponse> results = new ArrayList<>();
        for (DeadLetterWebhookRecord record : findRecords(merchantId, true)) {
            results.add(replay(record));
        }
        return results;
    }

    private List<DeadLetterWebhookRecord> findRecords(UUID merchantId, boolean unreplayedOnly) {
        if (merchantId != null) {
            List<DeadLetterWebhookRecord> records = dlqRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
            return unreplayedOnly ? records.stream().filter(r -> !r.isReplayed()).toList() : records;
        }
        return unreplayedOnly
                ? dlqRepository.findByReplayedFalseOrderByCreatedAtDesc()
                : dlqRepository.findAllByOrderByCreatedAtDesc();
    }

    private ReplayDlqResponse replay(DeadLetterWebhookRecord record) {
        UUID dlqId = record.getDlqId();
        log.info("Initiating DLQ replay for record: {}", dlqId);

        Optional<WebhookEndpoint> endpointOpt = endpointRepository.findByEndpointId(record.getEndpointId());
        if (endpointOpt.isEmpty()) {
            log.warn("Cannot replay DLQ record {}: Target endpoint {} no longer exists", dlqId, record.getEndpointId());
            return new ReplayDlqResponse(dlqId, "FAILED", 404, "Target endpoint no longer exists", Instant.now());
        }

        WebhookEndpoint endpoint = endpointOpt.get();
        // Authorisation was checked against the record's merchant. Legacy tables have no foreign key between a
        // record and its endpoint, so never send one merchant's payload to an endpoint owned by someone else.
        // Reported exactly like a missing endpoint so the other endpoint's existence is not revealed.
        if (!endpoint.getMerchantId().equals(record.getMerchantId())) {
            log.warn("Refusing DLQ replay {}: record merchant does not own target endpoint {}", dlqId, record.getEndpointId());
            return new ReplayDlqResponse(dlqId, "FAILED", 404, "Target endpoint no longer exists", Instant.now());
        }
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
        }
        int code = attempt != null ? attempt.httpStatusCode() : 500;
        String errorMsg = attempt != null && attempt.errorMessage() != null ? attempt.errorMessage() : "Dispatcher failure";
        log.warn("DLQ replay failed for record {}: {}", dlqId, errorMsg);

        return new ReplayDlqResponse(dlqId, "FAILED", code, "Replay attempt failed: " + errorMsg, Instant.now());
    }

    private static DlqRecordNotFoundException notFound(UUID dlqId) {
        return new DlqRecordNotFoundException("Dead-letter webhook record not found with ID: " + dlqId);
    }

    /**
     * Dispatchers receive operational metadata only. Withheld from them: the event payload (customer data), the
     * endpoint reference, and the raw failure text, which contains the merchant's callback URL or host. Everyone
     * gets the classification and sanitised summary, which never contain raw text.
     */
    private DeadLetterWebhookResponse mapToResponse(DeadLetterWebhookRecord record, CallerContext caller) {
        boolean fullDetail = caller.role() != UserRole.DISPATCHER;
        DlqFailureClassifier.Classification failure = DlqFailureClassifier.classify(record.getFailureReason());
        return new DeadLetterWebhookResponse(
                record.getDlqId(),
                fullDetail ? record.getEndpointId() : null,
                record.getMerchantId(),
                record.getEventType(),
                fullDetail ? record.getPayload() : null,
                fullDetail ? record.getFailureReason() : null,
                failure.errorClass(),
                failure.summary(),
                record.getTotalAttempts(),
                record.isReplayed(),
                record.getReplayedAt(),
                record.getCreatedAt()
        );
    }
}
