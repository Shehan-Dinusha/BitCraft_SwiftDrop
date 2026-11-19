package com.swiftdrop.webhook.delivery;

import com.swiftdrop.webhook.domain.entity.InboundEvent;
import com.swiftdrop.webhook.domain.entity.OrderMerchantMapping;
import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import com.swiftdrop.webhook.domain.repository.InboundEventRepository;
import com.swiftdrop.webhook.domain.repository.OrderMerchantMappingRepository;
import com.swiftdrop.webhook.domain.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Runs one claimed attempt end to end. Deliberately <b>not</b> transactional: the reads below are short read-only
 * repository calls, the HTTP request runs with no transaction or row lock held, and the result is stored by
 * {@link DeliveryAttemptRecorder#record} in its own short transaction.
 *
 * <ol>
 *   <li>Load the inbox event and the order ownership row; build the v1 payload bytes (Step 5).</li>
 *   <li>Load the endpoint <em>last</em>, immediately before sending. If it is paused, gone, or (defensively) owned by
 *       another merchant, release the claim: no request, no attempt row, the job returns to PENDING/RETRY and is
 *       picked up after reactivation (D8). This covers an endpoint paused between claim and send.</li>
 *   <li>Send with {@link WebhookHttpSender} (signing, SSRF and connect-time checks happen there, Steps 6–7).</li>
 *   <li>Record the outcome (retry policy decision + attempt row, fenced, atomic; Steps 8–9).</li>
 * </ol>
 * Any exception propagates to the worker, which leaves the lease to expire: lease recovery then closes the attempt as
 * {@code UNKNOWN} and the normal schedule applies, so a claimed job is never lost and never loops forever.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryProcessor {

    public enum Result {
        /** A request was sent (or refused by the URL policy) and the outcome was stored. */
        RECORDED,
        /** A request was sent but the lease no longer matched; the result was ignored (at-least-once). */
        STALE,
        /** Nothing was sent; the claim was given back (endpoint paused or gone). */
        RELEASED
    }

    private final InboundEventRepository inboundEventRepository;
    private final OrderMerchantMappingRepository mappingRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final WebhookPayloadFactory payloadFactory;
    private final WebhookHttpSender sender;
    private final DeliveryAttemptRecorder recorder;

    public Result process(ClaimedDelivery job) {
        InboundEvent event = inboundEventRepository.findById(job.inboundEventId())
                .orElseThrow(() -> new IllegalStateException("Inbound event of delivery " + job.deliveryId() + " not found"));
        OrderMerchantMapping mapping = Optional.ofNullable(event.getOrderId()).flatMap(mappingRepository::findById)
                .orElseThrow(() -> new IllegalStateException("Order mapping of delivery " + job.deliveryId() + " not found"));
        byte[] body = payloadFactory.buildBytes(PayloadSource.from(event, mapping));

        Optional<WebhookEndpoint> loaded = endpointRepository.findByEndpointId(job.endpointId());
        if (loaded.isEmpty() || !loaded.get().isActive() || !job.merchantId().equals(loaded.get().getMerchantId())) {
            boolean released = recorder.release(job);
            log.info("Delivery {} attempt {} not sent: endpoint {} is {}; claim {}", job.deliveryId(),
                    job.attemptNumber(), job.endpointId(), loaded.isEmpty() ? "missing" : "paused",
                    released ? "released" : "already changed");
            return Result.RELEASED;
        }
        WebhookEndpoint endpoint = loaded.get();

        WebhookRequest request = new WebhookRequest(endpoint.getTargetUrl(), endpoint.getSecretKey(), event.getEventId(),
                job.deliveryId(), job.eventType(), job.attemptNumber(), body);
        Instant attemptedAt = Instant.now();
        AttemptOutcome outcome = sender.send(request);
        DeliveryAttemptRecorder.RecordResult result = recorder.record(job, outcome, attemptedAt);
        return result.applied() ? Result.RECORDED : Result.STALE;
    }
}
