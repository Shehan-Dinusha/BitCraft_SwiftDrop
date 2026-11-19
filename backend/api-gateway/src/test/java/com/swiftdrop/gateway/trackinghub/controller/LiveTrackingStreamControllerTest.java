package com.swiftdrop.gateway.trackinghub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.gateway.trackinghub.client.OrderTrackingClient;
import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingViewDto;
import com.swiftdrop.gateway.trackinghub.service.TrackingBroadcastHubService;
import com.swiftdrop.gateway.trackinghub.service.TrackingDataSanitizerProxy;
import com.swiftdrop.gateway.trackinghub.service.TrackingViewEnrichmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveTrackingStreamControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final TrackingBroadcastHubService hub =
            new TrackingBroadcastHubService(new TrackingDataSanitizerProxy(), objectMapper);
    private final OrderTrackingClient orderClient = mock(OrderTrackingClient.class);
    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();
    private final List<WebSocketMessage> sent = new ArrayList<>();
    private LiveTrackingStreamController controller;

    @BeforeEach
    void setUp() {
        when(orderClient.getTracking(anyString())).thenReturn(Mono.just(Optional.empty()));
        TrackingViewEnrichmentService enrichmentService =
                new TrackingViewEnrichmentService(hub, orderClient, new TrackingDataSanitizerProxy());
        controller = new LiveTrackingStreamController(hub, objectMapper, enrichmentService);
    }

    @Test
    void emitsHeartbeatCommentsWhileWaitingForUpdates() {
        StepVerifier.withVirtualTime(() -> controller.stream("SD-WAIT-0000"))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(15))
                .expectNextMatches(event -> event.comment() != null && event.data() == null)
                .thenAwait(Duration.ofSeconds(15))
                .expectNextMatches(event -> event.comment() != null && event.data() == null)
                .thenCancel()
                .verify();
    }

    @Test
    void emitsCachedEnrichedViewOnSubscribe() {
        hub.updateCache("SD-CACHE-0000", new PublicTrackingViewDto(
                "SD-CACHE-0000",
                PublicOrderStatus.IN_TRANSIT,
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.KANDY,
                "Colombo 03",
                "Kandy",
                "N. P.",
                "07X***4567",
                "K. S.",
                "07X***1234",
                List.of()));

        StepVerifier.create(controller.stream("SD-CACHE-0000").take(1))
                .assertNext(event -> {
                    assertThat(event.data()).isNotNull();
                    assertThat(event.data().originCity()).isEqualTo("Colombo 03");
                    assertThat(event.data().senderPhoneMasked()).isEqualTo("07X***4567");
                })
                .verifyComplete();
    }

    @Test
    void completesTheStreamAfterATerminalStatus() {
        StepVerifier.create(controller.stream("SD-DONE-0000"))
                .expectSubscription()
                .then(() -> hub.onStatusChanged(event("SD-DONE-0000", PublicOrderStatus.DELIVERED)))
                .assertNext(event -> {
                    assertThat(event.data()).isNotNull();
                    assertThat(event.data().currentPublicStatus()).isEqualTo(PublicOrderStatus.DELIVERED);
                })
                .verifyComplete();
    }

    @Test
    void keepsTheStreamOpenForNonTerminalStatuses() {
        StepVerifier.withVirtualTime(() -> controller.stream("SD-OPEN-0000"))
                .expectSubscription()
                .then(() -> hub.onStatusChanged(event("SD-OPEN-0000", PublicOrderStatus.IN_TRANSIT)))
                .assertNext(event -> assertThat(event.data().currentPublicStatus())
                        .isEqualTo(PublicOrderStatus.IN_TRANSIT))
                .thenAwait(Duration.ofSeconds(15))
                .expectNextMatches(event -> event.comment() != null && event.data() == null)
                .thenCancel()
                .verify();
    }

    @Test
    void webSocketEmitsCachedViewAsTextMessage() {
        hub.updateCache("SD-WS-0000", view("SD-WS-0000", PublicOrderStatus.IN_TRANSIT));
        WebSocketSession session = webSocketSession("trackingNumber=SD-WS-0000");

        StepVerifier.create(controller.handle(session))
                .thenAwait(Duration.ofMillis(50))
                .thenCancel()
                .verify();

        assertThat(sent).anySatisfy(message -> {
            assertThat(message.getType()).isEqualTo(WebSocketMessage.Type.TEXT);
            assertThat(message.getPayloadAsText()).contains("SD-WS-0000");
        });
    }

    @Test
    void webSocketSendsPingHeartbeats() {
        WebSocketSession session = webSocketSession("trackingNumber=SD-WS-WAIT");

        StepVerifier.withVirtualTime(() -> controller.handle(session))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(15))
                .thenCancel()
                .verify();

        assertThat(sent).isNotEmpty();
        assertThat(sent).allSatisfy(message ->
                assertThat(message.getType()).isEqualTo(WebSocketMessage.Type.PING));
    }

    @Test
    void webSocketFallsBackToTheTrackingIdQueryParam() {
        hub.updateCache("SD-WS-LEGACY", view("SD-WS-LEGACY", PublicOrderStatus.PICKED_UP));
        WebSocketSession session = webSocketSession("trackingId=SD-WS-LEGACY");

        StepVerifier.create(controller.handle(session))
                .thenAwait(Duration.ofMillis(50))
                .thenCancel()
                .verify();

        assertThat(sent).anySatisfy(message -> {
            assertThat(message.getType()).isEqualTo(WebSocketMessage.Type.TEXT);
            assertThat(message.getPayloadAsText()).contains("SD-WS-LEGACY");
        });
    }

    @Test
    void webSocketUsesUnknownWhenNoTrackingParamIsPresent() {
        hub.updateCache("unknown", view("unknown", PublicOrderStatus.CREATED));
        WebSocketSession session = webSocketSession(null);

        StepVerifier.create(controller.handle(session))
                .thenAwait(Duration.ofMillis(50))
                .thenCancel()
                .verify();

        assertThat(sent).anySatisfy(message -> {
            assertThat(message.getType()).isEqualTo(WebSocketMessage.Type.TEXT);
            assertThat(message.getPayloadAsText()).contains("unknown");
        });
    }

    @Test
    void webSocketCompletesAfterATerminalStatus() {
        WebSocketSession session = webSocketSession("trackingNumber=SD-WS-DONE");

        StepVerifier.create(controller.handle(session))
                .expectSubscription()
                .then(() -> hub.onStatusChanged(event("SD-WS-DONE", PublicOrderStatus.DELIVERED)))
                .verifyComplete();

        assertThat(sent).anySatisfy(message -> {
            assertThat(message.getType()).isEqualTo(WebSocketMessage.Type.TEXT);
            assertThat(message.getPayloadAsText()).contains("DELIVERED");
        });
    }

    private WebSocketSession webSocketSession(String query) {
        HandshakeInfo handshakeInfo = mock(HandshakeInfo.class);
        URI uri = URI.create("ws://localhost/ws/tracking"
                + (query == null || query.isBlank() ? "" : "?" + query));
        when(handshakeInfo.getUri()).thenReturn(uri);

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getHandshakeInfo()).thenReturn(handshakeInfo);
        when(session.textMessage(anyString())).thenAnswer(invocation -> new WebSocketMessage(
                WebSocketMessage.Type.TEXT,
                bufferFactory.wrap(invocation.getArgument(0, String.class)
                        .getBytes(StandardCharsets.UTF_8))));
        when(session.pingMessage(any())).thenAnswer(invocation -> new WebSocketMessage(
                WebSocketMessage.Type.PING, bufferFactory.wrap(new byte[0])));
        when(session.send(any())).thenAnswer(invocation -> {
            Publisher<WebSocketMessage> messages = invocation.getArgument(0);
            return Flux.from(messages).doOnNext(sent::add).then();
        });
        return session;
    }

    private PublicTrackingViewDto view(String trackingNumber, PublicOrderStatus status) {
        return new PublicTrackingViewDto(
                trackingNumber,
                status,
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.KANDY,
                "Colombo 03",
                "Kandy",
                "N. P.",
                "07X***4567",
                "K. S.",
                "07X***1234",
                List.of());
    }

    private ParcelStatusChangedEvent event(String trackingNumber, PublicOrderStatus status) {
        return new ParcelStatusChangedEvent(
                UUID.randomUUID(), UUID.randomUUID(), trackingNumber, UUID.randomUUID(), UUID.randomUUID(),
                null, status, InternalQueueStatus.OUTBOUND,
                SriLankanDistrict.COLOMBO, SriLankanDistrict.KANDY, null, Instant.now());
    }
}
