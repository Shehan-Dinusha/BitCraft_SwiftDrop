package com.swiftdrop.webhook.service.subscription;

import com.swiftdrop.webhook.exception.InvalidEventSubscriptionException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An endpoint's event subscriptions, stored as a comma-separated list of patterns.
 *
 * <p>Grammar, deliberately small:</p>
 * <ul>
 *   <li><b>Exact</b>: a lower-case event type such as {@code parcel.status.changed}.</li>
 *   <li><b>Prefix wildcard</b>: {@code <segments>.*}, such as {@code parcel.*}. Matches every event type
 *       that starts with those whole segments followed by at least one more segment. {@code parcel.*} matches
 *       {@code parcel.status.changed} but not {@code parcelx.created} or {@code parcel} itself.</li>
 *   <li><b>All</b>: a lone {@code *}.</li>
 * </ul>
 * Anything else is invalid, including empty tokens, {@code *} inside a segment, and the legacy RabbitMQ
 * style {@code .#}. Invalid tokens never match anything.
 */
public final class EventSubscriptionPatterns {

    /** Must fit {@code webhook_endpoints.subscribed_events}. */
    public static final int MAX_STORED_LENGTH = 255;

    private static final String SEGMENT = "[a-z][a-z0-9_]*";
    private static final Pattern EXACT = Pattern.compile(SEGMENT + "(\\." + SEGMENT + ")*");
    private static final Pattern PREFIX = Pattern.compile("(" + SEGMENT + "(\\." + SEGMENT + ")*)\\.\\*");
    private static final String ALL = "*";

    private final List<String> patterns;

    private EventSubscriptionPatterns(List<String> patterns) {
        this.patterns = List.copyOf(patterns);
    }

    /**
     * Validates subscriptions supplied by an API caller. Every token must be well formed and match at least one
     * supported event type, so typos are rejected instead of silently never firing.
     *
     * @throws InvalidEventSubscriptionException listing every rejected token
     */
    public static EventSubscriptionPatterns parseForRegistration(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidEventSubscriptionException("At least one event subscription is required");
        }
        Set<String> accepted = new LinkedHashSet<>();
        List<String> rejected = new ArrayList<>();
        for (String token : raw.split(",", -1)) {
            String trimmed = token.trim();
            if (isWellFormed(trimmed) && matchesAnySupportedType(trimmed)) {
                accepted.add(trimmed);
            } else {
                rejected.add(trimmed.isEmpty() ? "<empty>" : trimmed);
            }
        }
        if (!rejected.isEmpty()) {
            throw new InvalidEventSubscriptionException("Unsupported event subscription(s): " + String.join(", ", rejected)
                    + ". Use an event type (" + supportedTypes() + "), a prefix such as parcel.*, or *");
        }
        EventSubscriptionPatterns parsed = new EventSubscriptionPatterns(new ArrayList<>(accepted));
        if (parsed.canonical().length() > MAX_STORED_LENGTH) {
            throw new InvalidEventSubscriptionException(
                    "Event subscriptions must not exceed " + MAX_STORED_LENGTH + " characters");
        }
        return parsed;
    }

    /**
     * Reads stored subscriptions for matching. Rows written before validation existed may hold invalid tokens;
     * those are dropped (they match nothing) rather than failing ingestion for the whole merchant.
     */
    public static EventSubscriptionPatterns parseStored(String stored) {
        if (stored == null) {
            return new EventSubscriptionPatterns(List.of());
        }
        return new EventSubscriptionPatterns(Arrays.stream(stored.split(","))
                .map(String::trim)
                .filter(EventSubscriptionPatterns::isWellFormed)
                .distinct()
                .toList());
    }

    public boolean matches(String eventType) {
        if (eventType == null || !EXACT.matcher(eventType).matches()) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern.equals(ALL) || pattern.equals(eventType)) {
                return true;
            }
            if (pattern.endsWith(".*") && eventType.startsWith(pattern.substring(0, pattern.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    /** Normalised form for storage: trimmed, de-duplicated, comma-separated in the caller's order. */
    public String canonical() {
        return String.join(",", patterns);
    }

    public List<String> patterns() {
        return patterns;
    }

    private static boolean isWellFormed(String token) {
        return token.equals(ALL) || EXACT.matcher(token).matches() || PREFIX.matcher(token).matches();
    }

    private static boolean matchesAnySupportedType(String token) {
        var single = new EventSubscriptionPatterns(List.of(token));
        return Arrays.stream(WebhookEventType.values()).anyMatch(type -> single.matches(type.eventType()));
    }

    private static String supportedTypes() {
        return String.join(", ", Arrays.stream(WebhookEventType.values()).map(WebhookEventType::eventType).toList());
    }
}
