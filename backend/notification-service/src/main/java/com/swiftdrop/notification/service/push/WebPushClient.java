package com.swiftdrop.notification.service.push;

import java.io.IOException;

/** Sends one encrypted message to one push service endpoint. */
public interface WebPushClient {

    /** False when VAPID keys are not configured: device push is skipped, the in-app inbox still works. */
    boolean enabled();

    /** The VAPID public key browsers subscribe with, or null when disabled. */
    String publicKey();

    /**
     * @param ttlSeconds how long the push service may hold the message for an offline device
     * @param urgency    {@code very-low}, {@code low}, {@code normal} or {@code high}
     * @param topic      replaces an undelivered message with the same topic (null for none)
     * @return the push service's HTTP status (201 when accepted, 404/410 when the subscription is gone)
     */
    int send(String endpoint, byte[] p256dh, byte[] auth, byte[] payload, int ttlSeconds, String urgency, String topic)
            throws IOException, InterruptedException;
}
