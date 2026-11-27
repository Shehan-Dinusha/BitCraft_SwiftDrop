package com.swiftdrop.webhook.service;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.webhook.domain.entity.DeadLetterWebhookRecord;
import com.swiftdrop.webhook.domain.repository.DeadLetterWebhookRecordRepository;
import com.swiftdrop.webhook.dto.DeadLetterWebhookResponse;
import com.swiftdrop.webhook.exception.DlqRecordNotFoundException;
import com.swiftdrop.webhook.service.access.MerchantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Legacy dead-letter records ({@code dead_letter_webhook_records}): read-only history.
 *
 * <p>The table was written by the retired synchronous dispatcher. Nothing writes it any more, and its records can no
 * longer be replayed (Step 17; the controller answers 410). Failed deliveries live in {@code webhook_deliveries} and
 * are listed and replayed by {@link FailedDeliveryService}. Unrelated to the RabbitMQ inbound dead-letter queue.</p>
 *
 * <p>Access: a {@code MERCHANT} sees its own records only; a {@code SYSTEM_ADMIN} sees every merchant's. Every
 * other role, including {@code DISPATCHER}, is refused. Another merchant's record is reported as not found.</p>
 */
@Service
@RequiredArgsConstructor
public class DlqReplayService {

    private static final UserRole[] READERS = {UserRole.MERCHANT, UserRole.SYSTEM_ADMIN};

    private final DeadLetterWebhookRecordRepository dlqRepository;

    @Transactional(readOnly = true)
    public List<DeadLetterWebhookResponse> listDlqRecords(UUID requestedMerchantId, Boolean unreplayedOnly,
                                                          CallerContext caller) {
        MerchantAccess.requireRole(caller, READERS);
        UUID merchantId = MerchantAccess.listScope(caller, requestedMerchantId, UserRole.SYSTEM_ADMIN);
        return findRecords(merchantId, Boolean.TRUE.equals(unreplayedOnly)).stream()
                .map(DlqReplayService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DeadLetterWebhookResponse getDlqRecord(UUID dlqId, CallerContext caller) {
        MerchantAccess.requireRole(caller, READERS);
        return dlqRepository.findByDlqId(dlqId)
                .filter(found -> MerchantAccess.canAccess(caller, found.getMerchantId()))
                .map(DlqReplayService::toResponse)
                .orElseThrow(() -> new DlqRecordNotFoundException("Dead-letter webhook record not found with ID: " + dlqId));
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

    /** The full record for its owner or an administrator, plus a classification and a sanitised summary. */
    private static DeadLetterWebhookResponse toResponse(DeadLetterWebhookRecord record) {
        DlqFailureClassifier.Classification failure = DlqFailureClassifier.classify(record.getFailureReason());
        return new DeadLetterWebhookResponse(
                record.getDlqId(),
                record.getEndpointId(),
                record.getMerchantId(),
                record.getEventType(),
                record.getPayload(),
                record.getFailureReason(),
                failure.errorClass(),
                failure.summary(),
                record.getTotalAttempts(),
                record.isReplayed(),
                record.getReplayedAt(),
                record.getCreatedAt()
        );
    }
}
