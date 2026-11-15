package com.swiftdrop.webhook.delivery;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Outbound delivery worker (Step 10). Every {@code webhook.delivery.poll-interval} it claims due jobs and runs each on
 * a virtual thread through {@link DeliveryProcessor}.
 *
 * <p><b>Limits (D9), per service instance:</b> at most {@code max-concurrency} attempts in flight (a semaphore) and
 * at most {@code max-per-endpoint} per endpoint (one semaphore per endpoint). The poll never claims more jobs than
 * free global capacity, capped at {@code claim-batch-size}; when capacity is exhausted it claims nothing. A claimed
 * job whose endpoint already has its local maximum in flight is released at once (no attempt) and is claimed again on
 * a later poll. These limits are <b>per replica</b>: with N replicas, up to N × max-concurrency attempts can run in the
 * cluster, and up to N × max-per-endpoint per endpoint.</p>
 *
 * <p><b>Ordering (D7)</b> is enforced by the claim query, cluster-wide: a job is not claimable while an older job of
 * the same endpoint and order is PENDING/RETRY/IN_FLIGHT, so one batch never holds two jobs of the same pair.</p>
 *
 * <p><b>Failures.</b> The poll catches everything, so one failure never stops scheduling. A job task always releases
 * its permits in {@code finally}. If processing throws, the lease is left to expire and {@link LeaseRecoveryJob}
 * closes the attempt; nothing is lost.</p>
 *
 * <p><b>Shutdown.</b> No new claims; running attempts get up to 15 s to finish and record. Anything still in flight
 * keeps its lease and is recovered after expiry on the next start (or by another replica).</p>
 *
 * <p>Disabled with {@code webhook.delivery.worker-enabled=false} (env {@code WEBHOOK_DELIVERY_WORKER_ENABLED}).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "webhook.delivery", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class DeliveryWorker {

    private static final long SHUTDOWN_WAIT_SECONDS = 15;

    private final DeliveryAttemptRecorder recorder;
    private final DeliveryProcessor processor;
    private final DeliveryProperties properties;
    private final String owner;
    private final Semaphore globalPermits;
    private final ConcurrentHashMap<UUID, Semaphore> endpointPermits = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean stopping;

    public DeliveryWorker(DeliveryAttemptRecorder recorder, DeliveryProcessor processor, DeliveryProperties properties) {
        this.recorder = recorder;
        this.processor = processor;
        this.properties = properties;
        this.globalPermits = new Semaphore(properties.maxConcurrency());
        String host = Optional.ofNullable(System.getenv("HOSTNAME")).filter(h -> !h.isBlank()).orElse("local");
        this.owner = "worker-" + host + "-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Delivery worker {} started: maxConcurrency={} maxPerEndpoint={} claimBatchSize={} pollInterval={} lease={}",
                owner, properties.maxConcurrency(), properties.maxPerEndpoint(), properties.claimBatchSize(),
                properties.pollInterval(), properties.leaseDuration());
    }

    @Scheduled(fixedDelayString = "${webhook.delivery.poll-interval:2s}", initialDelayString = "${webhook.delivery.poll-interval:2s}")
    public void poll() {
        if (stopping) {
            return;
        }
        try {
            int free = globalPermits.availablePermits();
            if (free == 0) {
                return;
            }
            List<ClaimedDelivery> jobs = recorder.claim(owner, Math.min(properties.claimBatchSize(), free));
            for (ClaimedDelivery job : jobs) {
                dispatch(job);
            }
        } catch (RuntimeException failure) {
            log.error("Delivery poll failed ({}); will retry on the next poll", failure.getClass().getSimpleName());
        }
    }

    private void dispatch(ClaimedDelivery job) {
        if (stopping || !globalPermits.tryAcquire()) {
            giveBack(job, "no capacity");
            return;
        }
        Semaphore endpointPermit = endpointPermits.computeIfAbsent(job.endpointId(),
                id -> new Semaphore(properties.maxPerEndpoint()));
        if (!endpointPermit.tryAcquire()) {
            globalPermits.release();
            giveBack(job, "endpoint busy");
            return;
        }
        try {
            executor.execute(() -> run(job, endpointPermit));
        } catch (RejectedExecutionException shuttingDown) {
            endpointPermit.release();
            globalPermits.release();
            giveBack(job, "worker stopping");
        }
    }

    private void run(ClaimedDelivery job, Semaphore endpointPermit) {
        MDC.put("deliveryId", job.deliveryId().toString());
        MDC.put("eventId", job.inboundEventId().toString());
        MDC.put("merchantId", job.merchantId().toString());
        MDC.put("endpointId", job.endpointId().toString());
        MDC.put("attempt", Integer.toString(job.attemptNumber()));
        try {
            processor.process(job);
        } catch (Throwable unexpected) {
            log.error("Delivery {} attempt {} (endpoint {}) failed unexpectedly ({}); its lease will expire and "
                            + "lease recovery will close the attempt", job.deliveryId(), job.attemptNumber(),
                    job.endpointId(), unexpected.getClass().getSimpleName());
        } finally {
            endpointPermit.release();
            globalPermits.release();
            MDC.clear();
        }
    }

    /** Returns a claimed job without an attempt (nothing was sent). Never throws. */
    private void giveBack(ClaimedDelivery job, String reason) {
        try {
            boolean released = recorder.release(job);
            log.debug("Delivery {} attempt {} handed back ({}): {}", job.deliveryId(), job.attemptNumber(), reason,
                    released ? "released" : "already changed");
        } catch (RuntimeException failure) {
            log.warn("Could not hand back delivery {} ({}); lease recovery will close it", job.deliveryId(),
                    failure.getClass().getSimpleName());
        }
    }

    /** Instance id used as {@code lease_owner} for every claim of this worker. */
    public String owner() {
        return owner;
    }

    @PreDestroy
    void shutdown() {
        stopping = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Delivery worker {} stopped with attempts still in flight; their leases will be recovered", owner);
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
