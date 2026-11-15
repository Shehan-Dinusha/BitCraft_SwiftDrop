package com.swiftdrop.webhook.delivery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically closes attempts whose lease expired (worker crashed, was killed, or stalled past the lease). Each such
 * attempt gets one {@code UNKNOWN} attempt row and the normal retry decision (RETRY on schedule, or DEAD if it was the
 * last attempt), via {@link DeliveryAttemptRecorder#recoverExpiredLeases}. Expired rows are locked with SKIP LOCKED, so
 * recovery running on several replicas never handles the same lease twice; a late result from the original worker is
 * then rejected by the fence. Recovery can add at most one attempt per lease and the attempt limit still applies, so it
 * cannot loop forever.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "webhook.delivery", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class LeaseRecoveryJob {

    private static final int BATCH = 100;

    private final DeliveryAttemptRecorder recorder;

    public LeaseRecoveryJob(DeliveryAttemptRecorder recorder) {
        this.recorder = recorder;
    }

    @Scheduled(fixedDelayString = "${webhook.delivery.lease-recovery-interval:30s}", initialDelayString = "10s")
    public void recover() {
        try {
            int recovered = recorder.recoverExpiredLeases(BATCH);
            if (recovered > 0) {
                log.warn("Recovered {} delivery attempt(s) with expired leases", recovered);
            }
        } catch (RuntimeException failure) {
            log.error("Lease recovery failed ({}); will retry", failure.getClass().getSimpleName());
        }
    }
}
