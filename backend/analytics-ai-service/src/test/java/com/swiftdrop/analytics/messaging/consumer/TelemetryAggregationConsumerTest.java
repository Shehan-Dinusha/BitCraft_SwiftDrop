package com.swiftdrop.analytics.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swiftdrop.analytics.messaging.TelemetryEvent;
import com.swiftdrop.analytics.messaging.TelemetryEventDecoder;
import com.swiftdrop.analytics.service.TelemetryProcessingService;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelemetryAggregationConsumerTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private TelemetryProcessingService processing;
    private TelemetryAggregationConsumer consumer;

    @BeforeEach
    void setUp() {
        processing = mock(TelemetryProcessingService.class);
        consumer = new TelemetryAggregationConsumer(new TelemetryEventDecoder(mapper), processing);
    }

    private ObjectNode status() {
        ObjectNode tree = mapper.createObjectNode();
        tree.put("eventId", UUID.randomUUID().toString());
        tree.put("orderId", UUID.randomUUID().toString());
        tree.put("correlationId", UUID.randomUUID().toString());
        tree.put("timestamp", "2026-11-08T06:00:00Z");
        tree.put("trackingNumber", "SD-TEST-001");
        tree.put("newPublicStatus", "DELIVERED");
        tree.put("district", "COLOMBO");
        return tree;
    }

    private Message message(String key, String body) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(key);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }

    @Test
    void preservesProducerIdentityTimeAndCorrelation() {
        ObjectNode tree = status();
        consumer.processMessage(message("parcel.status.changed", tree.toString()));
        var captor = org.mockito.ArgumentCaptor.forClass(TelemetryEvent.class);
        verify(processing).process(captor.capture());
        TelemetryEvent event = captor.getValue();
        assertThat(event.eventId().toString()).isEqualTo(tree.get("eventId").asText());
        assertThat(event.correlationId().toString()).isEqualTo(tree.get("correlationId").asText());
        assertThat(event.occurredAt().toString()).isEqualTo("2026-11-08T06:00:00Z");
        assertThat(event.payload()).isInstanceOf(ParcelStatusChangedEvent.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"eventId", "orderId", "correlationId", "timestamp", "trackingNumber", "district", "newPublicStatus"})
    void missingRequiredValueIsRejectedBeforeRecordDefaults(String field) {
        ObjectNode tree = status();
        tree.remove(field);
        assertThatThrownBy(() -> consumer.processMessage(message("parcel.status.changed", tree.toString())))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(processing);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "not-json", "null", "{} {}"})
    void malformedSupportedMessagesAreRejected(String body) {
        assertThatThrownBy(() -> consumer.processMessage(message("parcel.status.changed", body)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("Invalid supported analytics event: parcel.status.changed");
        verifyNoInteractions(processing);
    }

    @Test
    void rejectsInvalidUuidEnumAndTimestamp() {
        for (String field : new String[]{"eventId", "timestamp", "newPublicStatus"}) {
            ObjectNode tree = status();
            tree.put(field, "invalid");
            assertThatThrownBy(() -> consumer.processMessage(message("parcel.status.changed", tree.toString())))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        }
        verifyNoInteractions(processing);
    }

    @Test
    void acceptsSharedParcelAliasesAndInternalStatusMapping() {
        ObjectNode tree = status();
        tree.set("traceId", tree.remove("correlationId"));
        tree.remove("newPublicStatus");
        tree.put("internalSubStatus", "DELIVERED");
        consumer.processMessage(message("parcel.status.changed", tree.toString()));
        verify(processing).process(any(TelemetryEvent.class));
    }

    @Test
    void rejectsConflictingCorrelationAliases() {
        ObjectNode tree = status();
        tree.put("traceId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> consumer.processMessage(message("parcel.status.changed", tree.toString())))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(processing);
    }

    @Test
    void ignoresUnsupportedRoutingEvenWithMalformedOrMisleadingBody() {
        consumer.processMessage(message("dispatch.failed", "not-json"));
        consumer.processMessage(message("dispatch.future", status().toString()));
        verifyNoInteractions(processing);
    }

    @Test
    void rejectsDuplicateJsonKeys() {
        String body = status().toString().replaceFirst("\\{", "{\"eventId\":\"another-value\",");
        assertThatThrownBy(() -> consumer.processMessage(message("parcel.status.changed", body)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(processing);
    }

    @Test
    void forwardsDatabaseFailureForBoundedRetry() {
        doThrow(new IllegalStateException("database unavailable")).when(processing).process(any());
        assertThatThrownBy(() -> consumer.processMessage(message("parcel.status.changed", status().toString())))
                .isInstanceOf(IllegalStateException.class);
    }
}
