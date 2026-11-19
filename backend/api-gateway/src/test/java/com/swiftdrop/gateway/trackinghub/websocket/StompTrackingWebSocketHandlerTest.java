package com.swiftdrop.gateway.trackinghub.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.gateway.security.JwksKeyStore;
import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingViewDto;
import com.swiftdrop.gateway.trackinghub.service.TrackingBroadcastHubService;
import com.swiftdrop.gateway.trackinghub.service.TrackingDataSanitizerProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StompTrackingWebSocketHandlerTest {

    private static final String SECRET = "swiftdrop-test-secret-key-at-least-32-bytes-long!!";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final TrackingBroadcastHubService hub =
            new TrackingBroadcastHubService(new TrackingDataSanitizerProxy(), objectMapper);
    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();
    private final List<String> sentPayloads = new ArrayList<>();
    private final Sinks.Many<String> inbound = Sinks.many().unicast().onBackpressureBuffer();

    private StompTrackingWebSocketHandler handler;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        TrackingTokenVerifier tokenVerifier = new TrackingTokenVerifier(
                "HS256", SECRET, "swiftdrop-iam", "swiftdrop",
                mock(JwksKeyStore.class), objectMapper);
        handler = new StompTrackingWebSocketHandler(hub, objectMapper, tokenVerifier);

        session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");
        when(session.close()).thenReturn(Mono.empty());
        when(session.textMessage(anyString())).thenAnswer(invocation -> new WebSocketMessage(
                WebSocketMessage.Type.TEXT,
                bufferFactory.wrap(invocation.getArgument(0, String.class)
                        .getBytes(StandardCharsets.UTF_8))));
        when(session.receive()).thenReturn(inbound.asFlux()
                .map(frame -> new WebSocketMessage(
                        WebSocketMessage.Type.TEXT,
                        bufferFactory.wrap(frame.getBytes(StandardCharsets.UTF_8)))));
        when(session.send(any())).thenAnswer(invocation -> {
            Publisher<WebSocketMessage> messages = invocation.getArgument(0);
            return Flux.from(messages)
                    .doOnNext(message -> sentPayloads.add(message.getPayloadAsText()))
                    .then();
        });
    }

    @Test
    void connectSendsAConnectedFrame() {
        runWithFrames(connectFrame());

        assertThat(sentPayloads).anySatisfy(payload -> {
            assertThat(payload).startsWith("CONNECTED");
            assertThat(payload).contains("session:session-1");
        });
    }

    @Test
    void subscribeToAPublicTrackingTopicStreamsTheLatestView() {
        hub.updateCache("SD-STOMP-0001", view("SD-STOMP-0001"));

        runWithFrames(connectFrame(), subscribeFrame("sub-0", "/topic/track/SD-STOMP-0001"));

        assertThat(sentPayloads).anySatisfy(payload -> {
            assertThat(payload).startsWith("MESSAGE");
            assertThat(payload).contains("destination:/topic/track/SD-STOMP-0001");
            assertThat(payload).contains("SD-STOMP-0001");
        });
    }

    @Test
    void subscribeToAPrivateTopicWithoutATokenIsRejected() {
        runWithFrames(connectFrame(),
                subscribeFrame("sub-1", "/topic/customer/" + UUID.randomUUID()));

        assertThat(sentPayloads).anySatisfy(payload -> {
            assertThat(payload).startsWith("ERROR");
            assertThat(payload).contains("Not authorized");
        });
    }

    @Test
    void subscribeWithoutADestinationReturnsAnError() {
        runWithFrames(connectFrame(), "SUBSCRIBE\nid:sub-2\n\n\u0000");

        assertThat(sentPayloads).anySatisfy(payload -> {
            assertThat(payload).startsWith("ERROR");
            assertThat(payload).contains("requires a destination");
        });
    }

    @Test
    void unsupportedCommandReturnsAnError() {
        runWithFrames(connectFrame(), "BOGUS\n\n\u0000");

        assertThat(sentPayloads).anySatisfy(payload -> {
            assertThat(payload).startsWith("ERROR");
            assertThat(payload).contains("Unsupported STOMP command");
        });
    }

    @Test
    void disconnectClosesTheSessionAndCompletes() {
        StepVerifier.create(handler.handle(session))
                .then(() -> {
                    inbound.tryEmitNext(connectFrame());
                    inbound.tryEmitNext("DISCONNECT\nreceipt:1\n\n\u0000");
                    inbound.tryEmitComplete();
                })
                .verifyComplete();

        verify(session).close();
        assertThat(sentPayloads).anySatisfy(payload ->
                assertThat(payload).startsWith("RECEIPT"));
    }

    @Test
    void unsubscribeSendsAReceipt() {
        runWithFrames(connectFrame(),
                subscribeFrame("sub-3", "/topic/track/SD-STOMP-0002"),
                "UNSUBSCRIBE\nid:sub-3\nreceipt:2\n\n\u0000");

        assertThat(sentPayloads).anySatisfy(payload -> {
            assertThat(payload).startsWith("RECEIPT");
            assertThat(payload).contains("receipt-id:2");
        });
    }

    private void runWithFrames(String... frames) {
        StepVerifier.create(handler.handle(session))
                .then(() -> {
                    for (String frame : frames) {
                        inbound.tryEmitNext(frame);
                    }
                })
                .thenAwait(Duration.ofMillis(50))
                .thenCancel()
                .verify();
    }

    private String connectFrame() {
        return "CONNECT\naccept-version:1.2\nhost:localhost\n\n\u0000";
    }

    private String subscribeFrame(String id, String destination) {
        return "SUBSCRIBE\nid:" + id + "\ndestination:" + destination + "\nreceipt:r-" + id + "\n\n\u0000";
    }

    private PublicTrackingViewDto view(String trackingNumber) {
        return new PublicTrackingViewDto(
                trackingNumber,
                PublicOrderStatus.IN_TRANSIT,
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
}
