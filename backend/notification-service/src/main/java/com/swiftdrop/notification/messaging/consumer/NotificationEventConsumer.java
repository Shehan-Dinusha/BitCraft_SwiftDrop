package com.swiftdrop.notification.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.config.RabbitMQConfig;
import com.swiftdrop.notification.domain.entity.ProcessedEvent;
import com.swiftdrop.notification.domain.repository.ProcessedEventRepository;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.dto.NotificationDispatchLogResponse;
import com.swiftdrop.notification.messaging.trigger.EventEnvelope;
import com.swiftdrop.notification.messaging.trigger.NotificationTrigger;
import com.swiftdrop.notification.service.InAppNotificationService;
import com.swiftdrop.notification.service.MultiChannelNotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Consumes domain events from {@code notification.events.queue} and delegates
 * alert generation to registered {@link NotificationTrigger} observers.
 * Guarantees idempotency via {@link ProcessedEventRepository}.
 */
@Slf4j
@Component
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;
    private final MultiChannelNotificationDispatcher dispatcher;
    private final List<NotificationTrigger> triggers;
    private final ProcessedEventRepository processedEventRepository;
    private final InAppNotificationService inAppNotificationService;

    public NotificationEventConsumer(
            ObjectMapper objectMapper,
            MultiChannelNotificationDispatcher dispatcher,
            List<NotificationTrigger> triggers,
            ProcessedEventRepository processedEventRepository
    ) {
        this(objectMapper, dispatcher, triggers, processedEventRepository, null);
    }

    // Two constructors exist, so Spring needs to be told which one to inject;
    // without @Autowired it looks for a no-arg constructor and startup fails.
    @Autowired
    public NotificationEventConsumer(
            ObjectMapper objectMapper,
            MultiChannelNotificationDispatcher dispatcher,
            List<NotificationTrigger> triggers,
            ProcessedEventRepository processedEventRepository,
            InAppNotificationService inAppNotificationService
    ) {
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
        this.triggers = triggers;
        this.processedEventRepository = processedEventRepository;
        this.inAppNotificationService = inAppNotificationService;
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_EVENTS_QUEUE)
    public void onNotificationEvent(Message message) {
        Optional<EventEnvelope> envelopeOpt = EventEnvelope.from(message, objectMapper);
        if (envelopeOpt.isEmpty()) {
            log.warn("Skipping unparseable or empty event message");
            return;
        }

        EventEnvelope envelope = envelopeOpt.get();
        UUID eventId = envelope.eventId();

        // Idempotency check: ignore duplicate events
        if (eventId != null && processedEventRepository.existsById(eventId)) {
            log.info("Duplicate event detected with eventId: {}. Skipping notification dispatch.", eventId);
            return;
        }

        log.info("Processing event with routing key: {} (eventId: {}) across {} registered triggers",
                envelope.routingKey(), eventId, triggers.size());

        boolean matchedAny = false;
        // At most one inbox row per recipient per event, even if several requests or triggers match.
        Set<UUID> inboxRecipients = new HashSet<>();
        for (NotificationTrigger trigger : triggers) {
            if (trigger.matches(envelope)) {
                matchedAny = true;
                List<DispatchNotificationRequest> requests = trigger.build(envelope);
                if (requests != null && !requests.isEmpty()) {
                    for (DispatchNotificationRequest request : requests) {
                        NotificationDispatchLogResponse dispatchLog = dispatcher.dispatch(request);
                        if (request.recipientId() != null) {
                            String title = dispatchLog != null && dispatchLog.subject() != null
                                    ? dispatchLog.subject()
                                    : "SwiftDrop Notification";
                            String body = dispatchLog != null && dispatchLog.content() != null
                                    ? dispatchLog.content()
                                    : "Notification received";
                            String trackingNumber = dispatchLog != null && dispatchLog.referenceId() != null
                                    ? dispatchLog.referenceId()
                                    : envelope.extractTrackingNumber();

                            if (inAppNotificationService != null && inboxRecipients.add(request.recipientId())) {
                                inAppNotificationService.saveNotification(request.recipientId(), title, body, trackingNumber);

                                // For merchant orders, also write to merchant owner inbox if distinct
                                Optional<UUID> merchantOwnerId = envelope.extractMerchantOwnerId();
                                if (merchantOwnerId.isPresent() && inboxRecipients.add(merchantOwnerId.get())) {
                                    inAppNotificationService.saveNotification(merchantOwnerId.get(), title, body, trackingNumber);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!matchedAny) {
            log.debug("No notification trigger matched event with routing key: {}", envelope.routingKey());
        }

        // Record processed event to prevent future duplicates
        if (eventId != null) {
            try {
                processedEventRepository.save(new ProcessedEvent(eventId, envelope.routingKey(), Instant.now()));
            } catch (Exception e) {
                log.warn("Could not record processed event {}: {}", eventId, e.getMessage());
            }
        }
    }
}
