package com.swiftdrop.notification.service.push;

import com.swiftdrop.notification.util.SensitiveTemplates;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What a device push says for each notification type. Payloads pass through Google, Apple or Mozilla
 * (encrypted), so they hold only a short title, the tracking number and a link: no names, addresses,
 * phone numbers or amounts. Types not listed here are never pushed to devices: "order created" (the
 * customer just placed it), team alerts, and every security message (codes and sign-in alerts).
 */
public final class PushMessages {

    /** The tracking number is the only variable text, so anything else is dropped. */
    private static final Pattern TRACKING = Pattern.compile("^[A-Za-z0-9-]{1,32}$");

    static final int CUSTOMER_TTL_SECONDS = 24 * 60 * 60;
    /** A stale assignment alert is worse than none. */
    static final int COURIER_TTL_SECONDS = 15 * 60;

    /** @param urgency RFC 8030 Urgency header value */
    public record PushMessage(String title, String body, String url, String tag, int ttlSeconds, String urgency) {
    }

    private record Kind(String title, String text, boolean courier) {
    }

    private static final Map<String, Kind> KINDS = Map.of(
            "PARCEL_PICKUP_ASSIGNED", new Kind("Courier assigned", "%s · a courier is coming to collect it", false),
            "PARCEL_PICKED_UP", new Kind("Picked up", "%s has been collected", false),
            "PARCEL_OUT_FOR_DELIVERY", new Kind("Out for delivery", "%s is on its way", false),
            "PARCEL_DELIVERED", new Kind("Delivered", "%s has been delivered", false),
            "PARCEL_FAILED", new Kind("Delivery failed", "%s could not be delivered", false),
            "PARCEL_RETURNING", new Kind("Coming back", "%s is on its way back to you", false),
            "PARCEL_RETURNED", new Kind("Returned", "%s has been handed back", false),
            "COURIER_ASSIGNED", new Kind("New delivery assigned", "%s · open your deliveries", true));

    private PushMessages() {
    }

    public static boolean supports(String templateCode) {
        return templateCode != null && KINDS.containsKey(templateCode) && !SensitiveTemplates.isSecurity(templateCode);
    }

    public static Optional<PushMessage> forTemplate(String templateCode, Map<String, String> params) {
        if (!supports(templateCode)) {
            return Optional.empty();
        }
        Kind kind = KINDS.get(templateCode);
        String raw = params == null ? null : params.get("trackingNumber");
        String tracking = raw != null && TRACKING.matcher(raw.trim()).matches() ? raw.trim() : null;

        String body = tracking != null ? kind.text().formatted(tracking)
                : kind.courier() ? "Open SwiftDrop to see the job" : "Open SwiftDrop for the latest update";
        if (kind.courier()) {
            return Optional.of(new PushMessage(kind.title(), body, "/courier/deliveries", tracking,
                    COURIER_TTL_SECONDS, "high"));
        }
        String url = tracking != null ? "/customer/tracking/" + tracking : "/customer/orders";
        return Optional.of(new PushMessage(kind.title(), body, url, tracking, CUSTOMER_TTL_SECONDS, "normal"));
    }
}
