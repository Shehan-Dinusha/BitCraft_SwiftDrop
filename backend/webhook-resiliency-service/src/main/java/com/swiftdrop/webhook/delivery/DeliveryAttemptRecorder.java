package com.swiftdrop.webhook.delivery;

import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import com.swiftdrop.webhook.domain.repository.DeliveryJobRepository;
import com.swiftdrop.webhook.service.DlqFailureClassifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Transactional persistence of delivery attempts (Step 9). The delivery worker (Step 10) uses it as:
 * <ol>
 *   <li>{@link #claim}: short transaction; jobs become IN_FLIGHT with a lease and {@code attempt_count + 1};</li>
 *   <li>send the HTTP request <b>outside any transaction</b> ({@code WebhookHttpSender});</li>
 *   <li>{@link #record}: short transaction storing the result.</li>
 * </ol>
 *
 * <p><b>Atomicity.</b> {@link #record} runs the fenced job transition and the attempt-row insert in one transaction.
 * The transition runs first, so a stale result changes nothing and writes no attempt row. If the insert then fails
 * (for example a second row for the same attempt number, or any constraint violation), the exception rolls the
 * transition back too: there is never an attempt row without its job transition, or the reverse.</p>
 *
 * <p><b>Fence and stale results.</b> A result is stored only while the row is still IN_FLIGHT, leased to the same
 * owner, for the same attempt number. Lease recovery, a re-claim by another worker, or a terminal state (SUCCEEDED,
 * DEAD) all break the fence, so a late result can neither overwrite newer state nor reopen a finished delivery.
 * Two concurrent {@code record} calls for the same lease serialise on the row lock; the second one re-checks the
 * fence after the first commits and finds the row no longer IN_FLIGHT.</p>
 *
 * <p><b>Invariant.</b> Every claim increments {@code attempt_count}, and every claimed attempt ends in exactly one
 * attempt row: its real result via {@link #record}, or an {@code UNKNOWN} row via {@link #recoverExpiredLeases} if the
 * worker vanished. A claim requires {@code attempt_count < maxAttempts}, and the retry policy makes the last attempt
 * terminal, so no job ever exceeds the limit.</p>
 *
 * <p>Retry decisions come only from {@link DeliveryRetryPolicy}; nothing here re-classifies outcomes.</p>
 */
@Slf4j
@Service
public class DeliveryAttemptRecorder {

    private final DeliveryJobRepository repository;
    private final DeliveryRetryPolicy retryPolicy;
    private final DeliveryProperties properties;
    private final Clock clock;

    public DeliveryAttemptRecorder(DeliveryJobRepository repository, DeliveryRetryPolicy retryPolicy,
                                   DeliveryProperties properties) {
        this.repository = repository;
        this.retryPolicy = retryPolicy;
        this.properties = properties;
        this.clock = Clock.systemUTC();
    }

    /** Leases up to {@code limit} due jobs to {@code owner}, with the configured lease duration (D9). */
    @Transactional
    public List<ClaimedDelivery> claim(String owner, int limit) {
        return repository.claimDue(owner, properties.leaseDuration(), limit, retryPolicy.maxAttempts());
    }

    /**
     * Stores the result of one attempt of a claimed job: the policy's decision and one attempt row, atomically.
     *
     * @param attemptedAt when the HTTP attempt started
     * @return the applied decision, or {@link RecordResult#stale()} if the lease no longer matches (nothing written)
     */
    @Transactional
    public RecordResult record(ClaimedDelivery job, AttemptOutcome outcome, Instant attemptedAt) {
        RetryDecision decision = retryPolicy.decide(job.attemptNumber(), outcome);
        Optional<DeliveryJobRepository.AppliedTransition> applied = repository.completeAttempt(job.deliveryId(),
                job.leaseOwner(), job.attemptNumber(), decision.deliveryStatus(), decision.delay());
        if (applied.isEmpty()) {
            log.warn("Stale attempt result ignored: delivery {} attempt {} is no longer leased to this worker",
                    job.deliveryId(), job.attemptNumber());
            return RecordResult.stale();
        }
        repository.insertAttempt(UUID.randomUUID(), job, outcome.httpStatus(), outcome.success(), outcome.errorClass(),
                outcome.success() ? null : DlqFailureClassifier.describe(outcome.errorClass(), outcome.httpStatus()).summary(),
                outcome.latencyMs(), attemptedAt);
        log.info("Delivery {} attempt {} recorded: {}{} errorClass={} status={}", job.deliveryId(), job.attemptNumber(),
                decision.kind(), decision.deadReason() == null ? "" : "/" + decision.deadReason(), outcome.errorClass(),
                outcome.httpStatus());
        return new RecordResult(true, decision, applied.get().nextAttemptAt());
    }

    /**
     * Closes attempts whose worker vanished (lease expired): each gets an {@code UNKNOWN} attempt row with no status
     * or latency (the real outcome is unknown; the merchant may have received the request) and the normal retry
     * decision for that attempt number: RETRY on schedule, or DEAD if it was the last attempt. Expired rows are locked
     * with SKIP LOCKED, so concurrent recovery runs on several replicas never process the same job twice.
     *
     * @return number of recovered jobs
     */
    @Transactional
    public int recoverExpiredLeases(int limit) {
        List<ClaimedDelivery> expired = repository.lockExpiredLeases(limit);
        Instant now = clock.instant();
        int recovered = 0;
        for (ClaimedDelivery job : expired) {
            RetryDecision decision = retryPolicy.decide(job.attemptNumber(),
                    new AttemptOutcome(null, AttemptErrorClass.UNKNOWN, true, 0, null));
            if (repository.completeAttempt(job.deliveryId(), job.leaseOwner(), job.attemptNumber(),
                    decision.deliveryStatus(), decision.delay()).isEmpty()) {
                continue; // cannot happen while the row is locked; kept as a guard
            }
            repository.insertAttempt(UUID.randomUUID(), job, null, false, AttemptErrorClass.UNKNOWN,
                    "Outcome unknown: the worker's lease expired before it recorded a result", null, now);
            log.warn("Delivery {} attempt {} recovered after lease expiry: {}", job.deliveryId(), job.attemptNumber(),
                    decision.kind());
            recovered++;
        }
        return recovered;
    }

    /**
     * @param applied        false when the result was stale and nothing was written
     * @param decision       the policy decision that was applied (null when stale)
     * @param nextAttemptAt  stored {@code next_attempt_at} for RETRY (database clock), else null
     */
    public record RecordResult(boolean applied, RetryDecision decision, Instant nextAttemptAt) {
        public static RecordResult stale() {
            return new RecordResult(false, null, null);
        }
    }
}
