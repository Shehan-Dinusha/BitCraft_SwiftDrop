package com.swiftdrop.notification.messaging.trigger;

import com.swiftdrop.notification.dto.DispatchNotificationRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Observer interface for domain event notification triggers.
 * Implementing components declare which events they observe via {@link #matches(EventEnvelope)}
 * and construct corresponding notification dispatch requests via {@link #build(EventEnvelope)}.
 */
public interface NotificationTrigger {

    /**
     * Determines whether this trigger responds to the given event envelope.
     *
     * @param envelope the event envelope containing routing key and payload
     * @return {@code true} if this trigger should handle the event; {@code false} otherwise
     */
    boolean matches(EventEnvelope envelope);

    /**
     * Builds one or more notification requests based on the domain event.
     *
     * @param envelope the event envelope containing routing key and payload
     * @return list of notification dispatch requests, or empty list if no notification should be sent
     */
    List<DispatchNotificationRequest> build(EventEnvelope envelope);

    /**
     * Builds the SMS + EMAIL pair used for critical transitions (order confirmation,
     * out for delivery, failed). SMS comes first; the consumer writes one in-app row per recipient.
     */
    static List<DispatchNotificationRequest> smsAndEmail(UUID recipientId, String templateCode,
                                                         Map<String, String> params, String trackingNumber) {
        return List.of(
                new DispatchNotificationRequest(recipientId, "SMS", templateCode, null, params, trackingNumber),
                new DispatchNotificationRequest(recipientId, "EMAIL", templateCode, null, params, trackingNumber));
    }
}
