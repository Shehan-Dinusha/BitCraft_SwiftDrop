package com.swiftdrop.webhook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.webhook.delivery.AttemptOutcome;
import com.swiftdrop.webhook.delivery.WebhookHttpSender;
import com.swiftdrop.webhook.delivery.WebhookRequest;
import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import com.swiftdrop.webhook.domain.entity.WebhookDeliveryAttempt;
import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import com.swiftdrop.webhook.domain.repository.WebhookDeliveryAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestPingServiceTest {

    private final WebhookHttpSender sender = mock(WebhookHttpSender.class);
    private final WebhookDeliveryAttemptRepository attempts = mock(WebhookDeliveryAttemptRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final TestPingService service = new TestPingService(sender, attempts, mapper);
    private final WebhookEndpoint endpoint = WebhookEndpoint.builder().endpointId(UUID.randomUUID())
            .merchantId(UUID.randomUUID()).targetUrl("https://shop.example.com/hook").secretKey("whsec_test").build();

    @BeforeEach
    void saveReturnsItsArgument() {
        when(attempts.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void sendsTheSameEnvelopeAndSenderAsARealEvent() throws Exception {
        when(sender.send(any())).thenReturn(new AttemptOutcome(200, null, false, 42, null));

        var response = service.ping(endpoint);

        ArgumentCaptor<WebhookRequest> sent = ArgumentCaptor.forClass(WebhookRequest.class);
        verify(sender).send(sent.capture());
        JsonNode body = mapper.readTree(sent.getValue().body());
        assertThat(body.path("type").asText()).isEqualTo("webhook.test.ping");
        assertThat(body.path("apiVersion").asInt()).isEqualTo(1);
        assertThat(body.path("id").asText()).isEqualTo(sent.getValue().eventId().toString());
        assertThat(body.path("occurredAt").asText()).isNotBlank();
        assertThat(sent.getValue().secret()).isEqualTo("whsec_test");
        assertThat(response.success()).isTrue();
        assertThat(response.httpStatusCode()).isEqualTo(200);
    }

    @Test
    void recordsAFailureWithoutADeliveryJob() {
        when(sender.send(any())).thenReturn(new AttemptOutcome(500, AttemptErrorClass.HTTP_SERVER_ERROR, true, 10, null));

        var response = service.ping(endpoint);

        ArgumentCaptor<WebhookDeliveryAttempt> saved = ArgumentCaptor.forClass(WebhookDeliveryAttempt.class);
        verify(attempts).save(saved.capture());
        // The database wants exactly one of a delivery id and a payload.
        assertThat(saved.getValue().getDeliveryId()).isNull();
        assertThat(saved.getValue().getPayload()).isNotBlank();
        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("500");
    }
}
