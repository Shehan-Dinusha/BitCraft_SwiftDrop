package com.swiftdrop.notification.messaging.consumer;

import org.springframework.dao.DataAccessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.config.RabbitMQConfig;
import com.swiftdrop.notification.domain.entity.ProcessedEvent;
import com.swiftdrop.notification.domain.repository.ProcessedEventRepository;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.dto.NotificationDispatchLogResponse;
import com.swiftdrop.notification.messaging.trigger.EventEnvelope;
import com.swiftdrop.notification.messaging.trigger.NotificationTrigger;
import com.swiftdrop.notification.messaging.trigger.TeamNotification;
import com.swiftdrop.notification.service.InAppNotificationService;
import com.swiftdrop.notification.service.MerchantDirectory;
import com.swiftdrop.notification.service.push.DevicePushService;
import com.swiftdrop.notification.service.push.PushMessages;
import com.swiftdrop.notification.util.InboxAudience;
import com.swiftdrop.notification.service.MultiChannelNotificationDispatcher;
import com.swiftdrop.notification.util.SensitiveTemplates;
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
    private final MerchantDirectory merchantDirectory;
    private final DevicePushService devicePushService;

    public NotificationEventConsumer(
            ObjectMapper objectMapper,
            MultiChannelNotificationDispatcher dispatcher,
            List<NotificationTrigger> triggers,
            ProcessedEventRepository processedEventRepository
    ) {
        this(objectMapper, dispatcher, triggers, processedEventRepository, null, null);
    }

    public NotificationEventConsumer(
            ObjectMapper objectMapper,
            MultiChannelNotificationDispatcher dispatcher,
            List<NotificationTrigger> triggers,
            ProcessedEventRepository processedEventRepository,
            InAppNotificationService inAppNotificationService
    ) {
        this(objectMapper, dispatcher, triggers, processedEventRepository, inAppNotificationService, null);
    }

    // Several constructors exist, so Spring is told which one to inject (the widest, below);
    // without @Autowired it looks for a no-arg constructor and startup fails.
    public NotificationEventConsumer(
            ObjectMapper objectMapper,
            MultiChannelNotificationDispatcher dispatcher,
            List<NotificationTrigger> triggers,
            ProcessedEventRepository processedEventRepository,
            InAppNotificationService inAppNotificationService,
            MerchantDirectory merchantDirectory
    ) {
        this(objectMapper, dispatcher, triggers, processedEventRepository, inAppNotificationService, merchantDirectory, null);
    }

    @Autowired
    public NotificationEventConsumer(
            ObjectMapper objectMapper,
            MultiChannelNotificationDispatcher dispatcher,
            List<NotificationTrigger> triggers,
            ProcessedEventRepository processedEventRepository,
            InAppNotificationService inAppNotificationService,
            MerchantDirectory merchantDirectory,
            DevicePushService devicePushService
    ) {
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
        this.triggers = triggers;
        this.processedEventRepository = processedEventRepository;
        this.inAppNotificationService = inAppNotificationService;
        this.merchantDirectory = merchantDirectory;
        this.devicePushService = devicePushService;
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

        // A merchant order names the merchant company as its customer: remember the company so its parcel
        // updates go to the merchant team inbox and are never emailed to an id that is not a person.
        if (merchantDirectory != null) {
            envelope.merchantOrderMerchantId().ifPresent(merchantDirectory::remember);
        }

        boolean matchedAny = false;
        // At most one inbox row per recipient per event, even if several requests or triggers match.
        Set<UUID> inboxRecipients = new HashSet<>();
        Set<String> inboxAudiences = new HashSet<>();
        for (NotificationTrigger trigger : triggers) {
            if (trigger.matches(envelope)) {
                matchedAny = true;
                List<DispatchNotificationRequest> requests = trigger.build(envelope);
                if (requests != null && !requests.isEmpty()) {
                    for (DispatchNotificationRequest request : requests) {
                        if (!SensitiveTemplates.isSecurity(request.templateCode()) && isMerchantCompany(request.recipientId())) {
                            log.debug("{} is for merchant company {}; the merchant team inbox covers it",
                                    request.templateCode(), request.recipientId());
                            continue;
                        }
                        NotificationDispatchLogResponse dispatchLog = dispatcher.dispatch(request);
                        if (SensitiveTemplates.isSecurity(request.templateCode())) {
                            continue; // security mail (codes, sign-in alerts) is email only, never in the inbox
                        }
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

                            if (inboxRecipients.add(request.recipientId())) {
                                if (inAppNotificationService != null) {
                                    inAppNotificationService.saveNotification(request.recipientId(), request.templateCode(), title, body, trackingNumber);
                                }
                                // After the inbox row and the email, so a device push problem cannot affect either.
                                pushToDevices(request);
                            }
                        }
                    }
                }
                // Team inboxes (a merchant company, the operations team): one row per team, shared read state.
                if (inAppNotificationService != null) {
                    for (TeamNotification team : trigger.teamNotifications(envelope)) {
                        if (team != null && team.audience() != null && isKnownAudience(team.audience())
                                && inboxAudiences.add(team.audience())) {
                            inAppNotificationService.saveTeamNotification(team.audience(), team.notificationType(),
                                    team.title(), team.body(), team.trackingNumber());
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
            } catch (DataAccessException e) {
                // Best effort: the notification was already sent; a missing marker only risks a duplicate.
                log.warn("Could not record processed event {}: {}", eventId, e.getMessage());
            }
        }
    }

    private boolean isMerchantCompany(UUID id) {
        return merchantDirectory != null && merchantDirectory.isMerchant(id);
    }

    /** A merchant team row is written only for a known merchant company, never for a customer's own id. */
    private boolean isKnownAudience(String audience) {
        if (!audience.startsWith(InboxAudience.MERCHANT_PREFIX)) {
            return true;
        }
        try {
            return isMerchantCompany(UUID.fromString(audience.substring(InboxAudience.MERCHANT_PREFIX.length())));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Sends the lock-screen (Web Push) copy through the dispatcher's PUSH channel, which applies the
     * "push" preference, keeps security messages off devices and logs the attempt. Only some types are
     * pushed (see PushMessages), and only to recipients that turned device notifications on somewhere.
     */
    private void pushToDevices(DispatchNotificationRequest request) {
        if (devicePushService == null || !PushMessages.supports(request.templateCode())
                || !devicePushService.hasDevices(request.recipientId())) {
            return;
        }
        try {
            dispatcher.dispatch(new DispatchNotificationRequest(request.recipientId(), "PUSH", request.templateCode(),
                    null, request.templateParams(), request.referenceId()));
        } catch (RuntimeException e) {
            log.warn("Device push for {} failed: {}", request.recipientId(), e.getClass().getSimpleName());
        }
    }
}
