package com.swiftdrop.webhook.delivery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import com.swiftdrop.webhook.dto.outbound.OrderCreatedWebhookData;
import com.swiftdrop.webhook.dto.outbound.ParcelStatusChangedWebhookData;
import com.swiftdrop.webhook.dto.outbound.WebhookPayloadV1;
import com.swiftdrop.webhook.service.subscription.WebhookEventType;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;

/**
 * Builds the public merchant payload (v1, decision D6) from stored rows. Pure: no I/O, no clock, no randomness, so
 * the same {@link PayloadSource} always yields byte-identical JSON, and every retry of a delivery sends the same body.
 *
 * <p>Sources: {@code id} = inbox event id; {@code type} = canonical event type; {@code occurredAt} = the source
 * event's timestamp, truncated to microseconds (the precision PostgreSQL stores); {@code trackingNumber} = the order
 * ownership projection, never the status event (delivery-execution invents its own); {@code merchantOrderReference}
 * = always null until Step 18. Status and enum values are read by deserialising the stored payload into the shared
 * contract, so only valid contract values can appear.</p>
 *
 * <p>Deliberately excluded: customer id, correlation id, internal sub-status, districts, merchant id, database ids,
 * and every monetary/pricing field ({@code declaredValue}, {@code estimatedPrice},
 * {@code currency}) while D6 is provisional pending Member 7.</p>
 *
 * <p>Signing (Step 6) and sending (Step 7) must use exactly the bytes from {@link #buildBytes}.</p>
 */
@Component
public class WebhookPayloadFactory {

    private final ObjectMapper mapper;

    public WebhookPayloadFactory(ObjectMapper objectMapper) {
        // A private copy with every setting that affects the bytes pinned here, so global Jackson configuration
        // changes cannot silently alter the merchant-facing format.
        this.mapper = objectMapper.copy()
                .registerModule(new JavaTimeModule()) // no-op if the injected mapper already has it
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
    }

    public WebhookPayloadV1 build(PayloadSource source) {
        WebhookEventType type = WebhookEventType.fromEventType(source.eventType())
                .orElseThrow(() -> new IllegalArgumentException("No webhook payload for event type " + source.eventType()));
        Object data = switch (type) {
            case ORDER_CREATED -> orderCreated(source);
            case PARCEL_STATUS_CHANGED -> parcelStatusChanged(source);
        };
        return new WebhookPayloadV1(source.eventId(), type.eventType(), WebhookPayloadV1.API_VERSION,
                source.occurredAt().truncatedTo(ChronoUnit.MICROS), data);
    }

    /** The exact HTTP body. */
    public byte[] buildBytes(PayloadSource source) {
        try {
            return mapper.writeValueAsBytes(build(source));
        } catch (JsonProcessingException serialisationFailure) {
            throw new IllegalStateException("Could not serialise webhook payload for event " + source.eventId());
        }
    }

    private OrderCreatedWebhookData orderCreated(PayloadSource source) {
        OrderCreatedEvent event = read(source, OrderCreatedEvent.class);
        return new OrderCreatedWebhookData(event.orderId(), null, source.trackingNumber(), event.speed());
    }

    private ParcelStatusChangedWebhookData parcelStatusChanged(PayloadSource source) {
        ParcelStatusChangedEvent event = read(source, ParcelStatusChangedEvent.class);
        if (event.newPublicStatus() == null) {
            throw new IllegalArgumentException("Stored status event " + source.eventId() + " has no new status");
        }
        return new ParcelStatusChangedWebhookData(event.orderId(), null, source.trackingNumber(),
                event.newPublicStatus(), event.previousPublicStatus(), event.failureReason());
    }

    private <T> T read(PayloadSource source, Class<T> contract) {
        try {
            T event = mapper.treeToValue(source.storedPayload(), contract);
            if (event == null) {
                throw new IllegalArgumentException("Stored payload of event " + source.eventId() + " is empty");
            }
            return event;
        } catch (JsonProcessingException invalid) {
            // No payload text in the message: it could contain data that must not reach logs.
            throw new IllegalArgumentException("Stored payload of event " + source.eventId() + " does not match its contract");
        }
    }
}
