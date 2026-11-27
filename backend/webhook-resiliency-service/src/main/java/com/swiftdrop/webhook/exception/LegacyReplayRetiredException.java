package com.swiftdrop.webhook.exception;

/**
 * A legacy dead-letter replay was requested. That operation is retired (Step 17): it sent webhooks with the old
 * body-only signature, so it now answers 410 Gone and sends nothing. Failed deliveries are replayed through
 * {@code POST /api/v1/webhooks/deliveries/failed/{deliveryId}/replay}.
 */
public class LegacyReplayRetiredException extends RuntimeException {

    public static final String MESSAGE = "Legacy dead-letter replay was retired. Replay failed deliveries with "
            + "POST /api/v1/webhooks/deliveries/failed/{deliveryId}/replay.";

    public LegacyReplayRetiredException() {
        super(MESSAGE);
    }
}
