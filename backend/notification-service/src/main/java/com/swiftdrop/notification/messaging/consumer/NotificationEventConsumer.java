package com.swiftdrop.notification.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.config.RabbitMQConfig;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.messaging.trigger.EventEnvelope;
import com.swiftdrop.notification.messaging.trigger.NotificationTrigger;
import com.swiftdrop.notification.service.MultiChannelNotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Consumes domain events from {@code notification.events.queue} and delegates
 * alert generation to registered {@link NotificationTrigger} observers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;
    private final MultiChannelNotificationDispatcher dispatcher;
    private final List<NotificationTrigger> triggers;

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_EVENTS_QUEUE)
    public void onNotificationEvent(Message message) {
        Optional<EventEnvelope> envelopeOpt = EventEnvelope.from(message, objectMapper);
        if (envelopeOpt.isEmpty()) {
            log.warn("Skipping unparseable or empty event message");
            return;
        }

        EventEnvelope envelope = envelopeOpt.get();
        log.info("Processing event with routing key: {} across {} registered triggers",
                envelope.routingKey(), triggers.size());

        boolean matchedAny = false;
        for (NotificationTrigger trigger : triggers) {
            if (trigger.matches(envelope)) {
                matchedAny = true;
                List<DispatchNotificationRequest> requests = trigger.build(envelope);
                if (requests != null && !requests.isEmpty()) {
                    for (DispatchNotificationRequest request : requests) {
                        dispatcher.dispatch(request);
                    }
                }
            }
        }

        if (!matchedAny) {
            log.debug("No notification trigger matched event with routing key: {}", envelope.routingKey());
        }
    }
}
