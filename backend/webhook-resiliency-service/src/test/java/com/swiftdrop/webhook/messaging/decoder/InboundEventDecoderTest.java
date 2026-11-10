package com.swiftdrop.webhook.messaging.decoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swiftdrop.webhook.exception.NonRetryableIngestionException;
import com.swiftdrop.webhook.service.subscription.WebhookEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboundEventDecoderTest {

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    private final InboundEventDecoder decoder = new InboundEventDecoder(mapper);

    private ObjectNode statusEvent() {
        ObjectNode tree = mapper.createObjectNode();
        tree.put("eventId", UUID.randomUUID().toString());
        tree.put("correlationId", UUID.randomUUID().toString());
        tree.put("orderId", UUID.randomUUID().toString());
        tree.put("trackingNumber", "SD-1234-5678");
        tree.put("customerId", UUID.randomUUID().toString());
        tree.put("newPublicStatus", "IN_TRANSIT");
        tree.put("district", "COLOMBO");
        tree.put("timestamp", "2026-11-10T08:00:00Z");
        return tree;
    }

    private ObjectNode orderCreated(UUID merchantId) {
        ObjectNode tree = mapper.createObjectNode();
        tree.put("eventId", UUID.randomUUID().toString());
        tree.put("correlationId", UUID.randomUUID().toString());
        tree.put("orderId", UUID.randomUUID().toString());
        tree.put("trackingNumber", "SD-COL-20261110-1234");
        tree.put("customerId", UUID.randomUUID().toString());
        if (merchantId != null) {
            tree.put("merchantId", merchantId.toString());
        }
        tree.put("pickupDistrict", "COLOMBO");
        tree.put("deliveryDistrict", "KANDY");
        tree.put("timestamp", "2026-11-10T08:00:00Z");
        return tree;
    }

    private static Message message(String routingKey, String body) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }

    @Test
    void decodesStatusEventKeepingIdentifiersAndDroppingCustomerId() {
        ObjectNode tree = statusEvent();
        tree.put("unexpectedField", "not retained");
        DecodedInboundEvent event = decoder.decode(message("parcel.status.changed", tree.toString()));

        assertThat(event.type()).isEqualTo(WebhookEventType.PARCEL_STATUS_CHANGED);
        assertThat(event.eventId()).hasToString(tree.get("eventId").asText());
        assertThat(event.correlationId()).hasToString(tree.get("correlationId").asText());
        assertThat(event.orderId()).hasToString(tree.get("orderId").asText());
        assertThat(event.claimedMerchantId()).isNull();
        assertThat(event.schemaVersion()).isEqualTo(InboundEventDecoder.CURRENT_SCHEMA_VERSION);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-11-10T08:00:00Z"));
        assertThat(event.payload().has("customerId")).isFalse();
        assertThat(event.payload().has("unexpectedField")).isFalse();
        assertThat(event.payload().get("newPublicStatus").asText()).isEqualTo("IN_TRANSIT");
    }

    @Test
    void decodesOrderCreatedWithMerchantClaim() {
        UUID merchantId = UUID.randomUUID();
        DecodedInboundEvent event = decoder.decode(message("order.created", orderCreated(merchantId).toString()));
        assertThat(event.type()).isEqualTo(WebhookEventType.ORDER_CREATED);
        assertThat(event.claimedMerchantId()).isEqualTo(merchantId);
        assertThat(event.trackingNumber()).isEqualTo("SD-COL-20261110-1234");
        assertThat(event.payload().has("customerId")).isFalse();
    }

    @Test
    void orderCreatedWithoutMerchantIsACustomerOrder() {
        DecodedInboundEvent event = decoder.decode(message("order.created", orderCreated(null).toString()));
        assertThat(event.claimedMerchantId()).isNull();
    }

    @Test
    void acceptsNumericTimestampsWrittenByDefaultSpringAmqpConverters() {
        ObjectNode tree = orderCreated(UUID.randomUUID());
        tree.put("timestamp", new java.math.BigDecimal("1794384000.500000000"));
        assertThat(decoder.decode(message("order.created", tree.toString())).occurredAt())
                .isEqualTo(Instant.ofEpochSecond(1_794_384_000L, 500_000_000));
    }

    @Test
    void traceIdAliasIsNormalisedToCorrelationId() {
        ObjectNode tree = statusEvent();
        String traceId = tree.remove("correlationId").asText();
        tree.put("traceId", traceId);
        DecodedInboundEvent event = decoder.decode(message("parcel.status.changed", tree.toString()));
        assertThat(event.correlationId()).hasToString(traceId);
        assertThat(event.payload().get("correlationId").asText()).isEqualTo(traceId);
    }

    @Test
    void conflictingTraceIdAndCorrelationIdAreRejected() {
        ObjectNode tree = statusEvent();
        tree.put("traceId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> decoder.decode(message("parcel.status.changed", tree.toString())))
                .isInstanceOf(NonRetryableIngestionException.class);
    }

    @Test
    void moreSpecificParcelRoutingKeyIsNormalised() {
        DecodedInboundEvent event = decoder.decode(message("parcel.status.changed.delivered", statusEvent().toString()));
        assertThat(event.type()).isEqualTo(WebhookEventType.PARCEL_STATUS_CHANGED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"eventId", "correlationId", "orderId", "timestamp", "newPublicStatus"})
    void missingRequiredFieldIsRejectedWithoutGeneratingADefault(String field) {
        ObjectNode tree = statusEvent();
        tree.remove(field);
        assertThatThrownBy(() -> decoder.decode(message("parcel.status.changed", tree.toString())))
                .isInstanceOf(NonRetryableIngestionException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1", ""})
    void invalidUuidIsRejectedNamingTheFieldNotTheValue(String value) {
        ObjectNode tree = statusEvent();
        tree.put("eventId", value);
        assertThatThrownBy(() -> decoder.decode(message("parcel.status.changed", tree.toString())))
                .isInstanceOf(NonRetryableIngestionException.class)
                .hasMessage("Missing or invalid field 'eventId' in parcel.status.changed event");
    }

    @Test
    void invalidMerchantIdIsRejectedRatherThanTreatedAsCustomerOrder() {
        ObjectNode tree = orderCreated(null);
        tree.put("merchantId", "merchant-a");
        assertThatThrownBy(() -> decoder.decode(message("order.created", tree.toString())))
                .isInstanceOf(NonRetryableIngestionException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "[1,2]", "\"text\"", "{\"eventId\":1} trailing", "{\"a\":1,\"a\":2}"})
    void malformedBodiesAreRejected(String body) {
        assertThatThrownBy(() -> decoder.decode(message("parcel.status.changed", body)))
                .isInstanceOf(NonRetryableIngestionException.class);
    }

    @Test
    void unknownEnumValueIsRejected() {
        ObjectNode tree = statusEvent();
        tree.put("newPublicStatus", "TELEPORTED");
        assertThatThrownBy(() -> decoder.decode(message("parcel.status.changed", tree.toString())))
                .isInstanceOf(NonRetryableIngestionException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"webhook.dispatch.requested", "order.cancelled", "delivery.state.changed"})
    void unsupportedRoutingKeysAreRejected(String routingKey) {
        assertThatThrownBy(() -> decoder.decode(message(routingKey, statusEvent().toString())))
                .isInstanceOf(NonRetryableIngestionException.class)
                .hasMessageContaining("Unsupported routing key");
    }

    @Test
    void unsupportedSchemaVersionIsRejectedAndSupportedOneIsRecorded() {
        Message future = message("parcel.status.changed", statusEvent().toString());
        future.getMessageProperties().setHeader(InboundEventDecoder.SCHEMA_VERSION_HEADER, "2");
        assertThatThrownBy(() -> decoder.decode(future)).isInstanceOf(NonRetryableIngestionException.class);

        Message current = message("parcel.status.changed", statusEvent().toString());
        current.getMessageProperties().setHeader(InboundEventDecoder.SCHEMA_VERSION_HEADER, 1);
        assertThat(decoder.decode(current).schemaVersion()).isEqualTo("1");
    }

    @Test
    void sourceServiceComesFromAppIdWhenWellFormed() {
        Message valid = message("parcel.status.changed", statusEvent().toString());
        valid.getMessageProperties().setAppId("delivery-execution-service");
        assertThat(decoder.decode(valid).sourceService()).isEqualTo("delivery-execution-service");

        Message invalid = message("parcel.status.changed", statusEvent().toString());
        invalid.getMessageProperties().setAppId("bad id; drop table");
        assertThat(decoder.decode(invalid).sourceService()).isNull();
    }
}
