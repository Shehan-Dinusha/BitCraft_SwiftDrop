package com.swiftdrop.webhook.messaging.consumer;

import com.swiftdrop.webhook.config.RabbitMQConfig;
import com.swiftdrop.webhook.messaging.decoder.DecodedInboundEvent;
import com.swiftdrop.webhook.messaging.decoder.InboundEventDecoder;
import com.swiftdrop.webhook.service.ingestion.InboundEventIngestionService;
import com.swiftdrop.webhook.service.ingestion.IngestionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Single consumer of {@code webhook.events.queue}. It only ingests: it validates, persists and creates delivery
 * jobs. It never calls merchant endpoints, sleeps or retries in a loop; HTTP delivery is the worker's job.
 *
 * <p>Returning normally lets the container acknowledge the message, which happens only after
 * {@link InboundEventIngestionService#ingest} has committed. Any exception leaves the message unacknowledged
 * and goes through the bounded retry and dead-letter policy in {@link RabbitMQConfig}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InboundEventConsumer {

    public static final String LISTENER_ID = "webhookInboundEvents";

    private final InboundEventDecoder decoder;
    private final InboundEventIngestionService ingestionService;

    @RabbitListener(id = LISTENER_ID, queues = RabbitMQConfig.WEBHOOK_EVENTS_QUEUE,
            containerFactory = RabbitMQConfig.INGESTION_LISTENER_FACTORY)
    public void onMessage(Message message) {
        DecodedInboundEvent event = decoder.decode(message);
        try (var ignoredEvent = MDC.putCloseable("eventId", event.eventId().toString());
             var ignoredCorrelation = MDC.putCloseable("correlationId", event.correlationId().toString());
             var ignoredType = MDC.putCloseable("eventType", event.type().eventType())) {
            IngestionResult result = ingestionService.ingest(event);
            log.info("Ingested eventId={} eventType={} correlationId={} orderId={} outcome={} deliveries={} "
                            + "releasedParkedEvents={} releasedDeliveries={}",
                    event.eventId(), event.type().eventType(), event.correlationId(), event.orderId(),
                    result.outcome(), result.deliveriesCreated(), result.releasedParkedEvents(),
                    result.releasedDeliveries());
        }
    }
}
