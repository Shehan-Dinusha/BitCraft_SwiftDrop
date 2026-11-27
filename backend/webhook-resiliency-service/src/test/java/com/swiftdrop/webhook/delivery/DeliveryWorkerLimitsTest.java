package com.swiftdrop.webhook.delivery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The per-replica limits of {@link DeliveryWorker} (D9): global capacity, one attempt per endpoint, and claims never
 * larger than free capacity. Processing is held on a latch, so "in flight" is exact rather than timing-dependent.
 */
class DeliveryWorkerLimitsTest {

    private final DeliveryAttemptRecorder recorder = mock(DeliveryAttemptRecorder.class);
    private final DeliveryProcessor processor = mock(DeliveryProcessor.class);
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final CountDownLatch finish = new CountDownLatch(1);
    private DeliveryWorker worker;

    private DeliveryWorker worker(int maxConcurrency, int maxPerEndpoint, int claimBatchSize) {
        var properties = new DeliveryProperties(List.of(Duration.ofSeconds(30)), 0.0, Duration.ofMinutes(10),
                maxConcurrency, maxPerEndpoint, claimBatchSize, Duration.ofSeconds(2), Duration.ofSeconds(60));
        when(processor.process(any())).thenAnswer(call -> {
            ClaimedDelivery job = call.getArgument(0);
            inFlight.add(job.deliveryId());
            try {
                finish.await(20, TimeUnit.SECONDS);
            } finally {
                inFlight.remove(job.deliveryId());
            }
            return DeliveryProcessor.Result.RECORDED;
        });
        worker = new DeliveryWorker(recorder, processor, properties);
        return worker;
    }

    @AfterEach
    void stop() {
        finish.countDown();
        if (worker != null) {
            worker.shutdown();
        }
    }

    private static ClaimedDelivery job(UUID endpoint) {
        return new ClaimedDelivery(UUID.randomUUID(), UUID.randomUUID(), endpoint, UUID.randomUUID(),
                "parcel.status.changed", 1, "w", Instant.now().plusSeconds(60));
    }

    @Test
    @DisplayName("a second job for a busy endpoint is handed back unsent; other endpoints still run")
    void perEndpointLimitHandsBackTheSecondJob() {
        DeliveryWorker worker = worker(4, 1, 10);
        UUID busy = UUID.randomUUID(), other = UUID.randomUUID();
        ClaimedDelivery first = job(busy), second = job(busy), third = job(other);
        when(recorder.claim(eq(worker.owner()), anyInt())).thenReturn(List.of(first, second, third), List.of());

        worker.poll();

        await().atMost(Duration.ofSeconds(10)).until(() -> inFlight.size() == 2);
        assertThat(inFlight).containsExactlyInAnyOrder(first.deliveryId(), third.deliveryId());
        verify(recorder).release(second);
        verify(processor, never()).process(second);
        verify(recorder, never()).release(first);
        verify(recorder, never()).release(third);
    }

    @Test
    @DisplayName("claims never exceed free capacity, and nothing is claimed while the worker is full")
    void globalCapacityBoundsClaimsAndInFlightAttempts() {
        DeliveryWorker worker = worker(2, 1, 10);
        ClaimedDelivery a = job(UUID.randomUUID()), b = job(UUID.randomUUID());
        when(recorder.claim(eq(worker.owner()), anyInt())).thenReturn(List.of(a, b), List.of());

        worker.poll();
        verify(recorder).claim(worker.owner(), 2); // min(batch 10, free 2)
        await().atMost(Duration.ofSeconds(10)).until(() -> inFlight.size() == 2);

        worker.poll();
        worker.poll();
        verify(recorder, times(1)).claim(any(), anyInt()); // full: no further claims

        finish.countDown();
        // Permits came back once the attempts finished: a later poll asks for full capacity again.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            worker.poll();
            verify(recorder, org.mockito.Mockito.atLeast(2)).claim(worker.owner(), 2);
        });
    }

    @Test
    @DisplayName("a claim larger than the remaining capacity hands the excess back unsent")
    void excessClaimsAreHandedBack() {
        DeliveryWorker worker = worker(2, 1, 10);
        ClaimedDelivery a = job(UUID.randomUUID()), b = job(UUID.randomUUID()), c = job(UUID.randomUUID());
        when(recorder.claim(eq(worker.owner()), anyInt())).thenReturn(List.of(a, b, c), List.of());

        worker.poll();

        await().atMost(Duration.ofSeconds(10)).until(() -> inFlight.size() == 2);
        verify(recorder).release(c);
        verify(processor, never()).process(c);
    }

    @Test
    @DisplayName("a processing failure gives the permits back; the lease is left for recovery, not released")
    void failureReleasesPermitsButNotTheLease() {
        var properties = new DeliveryProperties(List.of(Duration.ofSeconds(30)), 0.0, Duration.ofMinutes(10),
                1, 1, 10, Duration.ofSeconds(2), Duration.ofSeconds(60));
        UUID endpoint = UUID.randomUUID();
        ClaimedDelivery failing = job(endpoint), next = job(endpoint);
        when(processor.process(failing)).thenThrow(new IllegalStateException("boom"));
        when(processor.process(next)).thenReturn(DeliveryProcessor.Result.RECORDED);
        worker = new DeliveryWorker(recorder, processor, properties);
        when(recorder.claim(eq(worker.owner()), anyInt())).thenReturn(List.of(failing), List.of(next), List.of());

        worker.poll();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> verify(processor).process(failing));
        // Global and endpoint permits are free again, so the same endpoint's next job runs.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            worker.poll();
            verify(processor).process(next);
        });
        verify(recorder, never()).release(failing);
    }
}
