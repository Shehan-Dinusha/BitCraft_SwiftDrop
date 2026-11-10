package com.swiftdrop.webhook.messaging.decoder;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import com.swiftdrop.webhook.exception.NonRetryableIngestionException;
import com.swiftdrop.webhook.service.subscription.WebhookEventType;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validates raw broker messages against the shared event contracts.
 *
 * <p>The type is chosen from the routing key, never from the producer-supplied {@code __TypeId__} header.
 * Required fields are checked on the raw JSON first, because the shared records' compact constructors would
 * otherwise invent a random {@code eventId}/{@code correlationId} or the current time for missing values, and a
 * random event id would defeat deduplication.</p>
 *
 * <p>Every rejection is a {@link NonRetryableIngestionException} whose message names a field, never a value.</p>
 */
@Component
public class InboundEventDecoder {

    /**
     * Optional header carrying the producer's contract version. Producers do not send it yet; an absent header
     * means the current, unversioned contract, recorded as version {@value #CURRENT_SCHEMA_VERSION}.
     */
    public static final String SCHEMA_VERSION_HEADER = "x-event-schema-version";
    public static final String CURRENT_SCHEMA_VERSION = "1";
    private static final Set<String> SUPPORTED_SCHEMA_VERSIONS = Set.of(CURRENT_SCHEMA_VERSION);

    /** Personal identifiers merchants never receive and the inbox therefore does not keep. */
    private static final Set<String> NON_PERSISTED_FIELDS = Set.of("customerId");

    private static final Pattern SOURCE_SERVICE = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private static final int MAX_TRACKING_NUMBER_LENGTH = 64;

    private final ObjectMapper objectMapper;

    public InboundEventDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    public DecodedInboundEvent decode(Message message) {
        MessageProperties properties = message.getMessageProperties();
        String routingKey = properties.getReceivedRoutingKey();
        WebhookEventType type = WebhookEventType.fromRoutingKey(routingKey)
                .orElseThrow(() -> new NonRetryableIngestionException("Unsupported routing key: " + routingKey));
        String schemaVersion = schemaVersion(properties);

        JsonNode tree;
        try {
            tree = objectMapper.readTree(message.getBody());
        } catch (Exception malformed) {
            throw new NonRetryableIngestionException("Malformed JSON body for " + type.eventType());
        }
        if (tree == null || !tree.isObject()) {
            throw new NonRetryableIngestionException("Body of " + type.eventType() + " is not a JSON object");
        }
        ObjectNode object = (ObjectNode) tree;

        UUID eventId = requiredUuid(object, "eventId", type);
        UUID orderId = requiredUuid(object, "orderId", type);
        UUID correlationId = correlationId(object, type);
        if (!object.hasNonNull("timestamp")) {
            throw invalid(type, "timestamp");
        }

        Record event;
        try {
            event = objectMapper.treeToValue(object, type.contract());
        } catch (Exception invalidShape) {
            throw new NonRetryableIngestionException("Event " + type.eventType() + " does not match its contract");
        }

        UUID claimedMerchantId = null;
        String trackingNumber;
        Instant occurredAt;
        switch (event) {
            case OrderCreatedEvent created -> {
                if (object.hasNonNull("merchantId")) {
                    claimedMerchantId = requiredUuid(object, "merchantId", type);
                }
                trackingNumber = created.trackingNumber();
                occurredAt = created.timestamp();
            }
            case ParcelStatusChangedEvent changed -> {
                if (changed.newPublicStatus() == null) {
                    throw invalid(type, "newPublicStatus");
                }
                trackingNumber = changed.trackingNumber();
                occurredAt = changed.timestamp();
            }
            default -> throw new IllegalStateException("No decoder for " + type);
        }

        ObjectNode payload = objectMapper.valueToTree(event);
        payload.remove(NON_PERSISTED_FIELDS);

        return new DecodedInboundEvent(eventId, type, schemaVersion, sourceService(properties), correlationId,
                orderId, claimedMerchantId, boundedTrackingNumber(trackingNumber), occurredAt, payload);
    }

    private static String schemaVersion(MessageProperties properties) {
        Object header = properties.getHeaders().get(SCHEMA_VERSION_HEADER);
        if (header == null) {
            return CURRENT_SCHEMA_VERSION;
        }
        String version = String.valueOf(header).trim();
        if (!SUPPORTED_SCHEMA_VERSIONS.contains(version)) {
            throw new NonRetryableIngestionException("Unsupported event schema version");
        }
        return version;
    }

    /**
     * {@code ParcelStatusChangedEvent} accepts {@code traceId} as an alias. Both present with different values
     * is ambiguous and rejected, rather than letting JSON field order decide.
     */
    private static UUID correlationId(ObjectNode object, WebhookEventType type) {
        boolean aliasAllowed = type == WebhookEventType.PARCEL_STATUS_CHANGED;
        if (aliasAllowed && object.hasNonNull("traceId")) {
            UUID traceId = requiredUuid(object, "traceId", type);
            if (object.hasNonNull("correlationId") && !requiredUuid(object, "correlationId", type).equals(traceId)) {
                throw invalid(type, "correlationId");
            }
            object.put("correlationId", traceId.toString());
            object.remove("traceId");
            return traceId;
        }
        return requiredUuid(object, "correlationId", type);
    }

    private static UUID requiredUuid(ObjectNode object, String field, WebhookEventType type) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual()) {
            throw invalid(type, field);
        }
        try {
            UUID id = UUID.fromString(value.textValue());
            // UUID.fromString accepts non-canonical forms such as "1-1-1-1-1".
            if (!id.toString().equalsIgnoreCase(value.textValue())) {
                throw invalid(type, field);
            }
            return id;
        } catch (IllegalArgumentException notUuid) {
            throw invalid(type, field);
        }
    }

    private static String sourceService(MessageProperties properties) {
        String appId = properties.getAppId();
        return appId != null && SOURCE_SERVICE.matcher(appId).matches() ? appId : null;
    }

    private static String boundedTrackingNumber(String trackingNumber) {
        return trackingNumber != null && trackingNumber.length() <= MAX_TRACKING_NUMBER_LENGTH ? trackingNumber : null;
    }

    private static NonRetryableIngestionException invalid(WebhookEventType type, String field) {
        return new NonRetryableIngestionException(
                "Missing or invalid field '" + field + "' in " + type.eventType() + " event");
    }
}
