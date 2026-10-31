package com.swiftdrop.gateway.trackinghub.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingViewDto;
import com.swiftdrop.gateway.trackinghub.service.TrackingBroadcastHubService;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal STOMP 1.2 WebSocket handler for public tracking and private customer
 * topics. It supports the CONNECT, SUBSCRIBE, UNSUBSCRIBE, and DISCONNECT frames
 * needed by browser clients without pulling in a full messaging broker.
 *
 * <p>Public tracking topics: {@code /topic/track/{trackingNumber}}.
 * Private customer topics: {@code /topic/customer/{customerId}}. Authorization
 * for private topics is enforced by the gateway handshake/JWT layer.</p>
 */
@Component
public class StompTrackingWebSocketHandler implements WebSocketHandler {

    private static final String TRACK_TOPIC_PREFIX = "/topic/track/";

    private final TrackingBroadcastHubService broadcastHub;
    private final ObjectMapper objectMapper;
    private final TrackingTokenVerifier tokenVerifier;

    public StompTrackingWebSocketHandler(TrackingBroadcastHubService broadcastHub,
                                         ObjectMapper objectMapper,
                                         TrackingTokenVerifier tokenVerifier) {
        this.broadcastHub = broadcastHub;
        this.objectMapper = objectMapper;
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    @NonNull
    public Mono<Void> handle(@NonNull WebSocketSession session) {
        Sinks.Many<String> outgoing = Sinks.many().unicast().onBackpressureBuffer();
        StompSessionState state = new StompSessionState(session, outgoing);

        Mono<Void> send = session.send(outgoing.asFlux().map(session::textMessage));
        Mono<Void> receive = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(state::handleFrame)
                .doFinally(signal -> {
                    state.dispose();
                    outgoing.tryEmitComplete();
                })
                .then();

        return Mono.when(send, receive)
                .doFinally(signal -> state.dispose());
    }

    private final class StompSessionState {

        private final WebSocketSession session;
        private final Sinks.Many<String> outgoing;
        private final Map<String, Disposable> subscriptions = new ConcurrentHashMap<>();
        private TrackingTokenVerifier.StompPrincipal principal;

        private StompSessionState(WebSocketSession session, Sinks.Many<String> outgoing) {
            this.session = session;
            this.outgoing = outgoing;
        }

        private void handleFrame(String rawFrame) {
            if (rawFrame == null || rawFrame.isBlank()) {
                return;
            }

            for (String frame : rawFrame.split("\u0000")) {
                String cleaned = frame.replace("\r", "").stripLeading();
                if (cleaned.isBlank()) {
                    continue;
                }

                String[] lines = cleaned.split("\n", -1);
                String command = lines[0].strip().toUpperCase(Locale.ROOT);
                Map<String, String> headers = parseHeaders(lines);

                switch (command) {
                    case "CONNECT", "STOMP" -> connect(headers);
                    case "SUBSCRIBE" -> subscribe(headers);
                    case "UNSUBSCRIBE" -> unsubscribe(headers);
                    case "DISCONNECT" -> disconnect(headers);
                    case "SEND" -> sendReceipt(headers);
                    default -> sendError("Unsupported STOMP command");
                }
            }
        }

        private Map<String, String> parseHeaders(String[] lines) {
            Map<String, String> headers = new LinkedHashMap<>();
            for (int index = 1; index < lines.length; index++) {
                String line = lines[index];
                if (line.isBlank()) {
                    break;
                }
                int separator = line.indexOf(':');
                if (separator > 0) {
                    headers.put(line.substring(0, separator).trim(),
                            line.substring(separator + 1).trim());
                }
            }
            return headers;
        }

        private void connect(Map<String, String> headers) {
            String authorization = headers.getOrDefault("Authorization",
                    headers.get("authorization"));
            principal = tokenVerifier.verify(authorization);
            sendConnected();
        }

        private void sendConnected() {
            String sessionId = session.getId();
            outgoing.tryEmitNext(
                    "CONNECTED\n"
                            + "version:1.2\n"
                            + "heart-beat:0,0\n"
                            + "session:" + sessionId + "\n"
                            + "server:swiftdrop-gateway/1.0\n"
                            + "\n\u0000"
            );
        }

        private void subscribe(Map<String, String> headers) {
            String destination = headers.get("destination");
            if (destination == null || destination.isBlank()) {
                sendError("SUBSCRIBE requires a destination");
                return;
            }
            if (!tokenVerifier.maySubscribe(principal, destination)) {
                sendError("Not authorized for this topic");
                return;
            }

            String subscriptionId = headers.getOrDefault("id", UUID.randomUUID().toString());
            Disposable previous = subscriptions.remove(subscriptionId);
            if (previous != null) {
                previous.dispose();
            }

            Disposable subscription = broadcastHub.streamTopic(destination)
                    .subscribe(payload -> sendMessage(destination, subscriptionId, payload));
            subscriptions.put(subscriptionId, subscription);

            if (destination.startsWith(TRACK_TOPIC_PREFIX)) {
                String trackingNumber = destination.substring(TRACK_TOPIC_PREFIX.length());
                PublicTrackingViewDto latest = broadcastHub.current(trackingNumber);
                if (latest != null) {
                    String json = toJson(latest);
                    if (json != null) {
                        sendMessage(destination, subscriptionId, json);
                    }
                }
            }

            sendReceipt(headers);
        }

        private void unsubscribe(Map<String, String> headers) {
            String subscriptionId = headers.get("id");
            if (subscriptionId != null) {
                Disposable subscription = subscriptions.remove(subscriptionId);
                if (subscription != null) {
                    subscription.dispose();
                }
            }
            sendReceipt(headers);
        }

        private void disconnect(Map<String, String> headers) {
            sendReceipt(headers);
            dispose();
            outgoing.tryEmitComplete();
            session.close().subscribe();
        }

        private void sendMessage(String destination, String subscriptionId, String payload) {
            int contentLength = payload.getBytes(StandardCharsets.UTF_8).length;
            outgoing.tryEmitNext(
                    "MESSAGE\n"
                            + "destination:" + destination + "\n"
                            + "subscription:" + subscriptionId + "\n"
                            + "message-id:" + UUID.randomUUID() + "\n"
                            + "content-type:application/json\n"
                            + "content-length:" + contentLength + "\n"
                            + "\n"
                            + payload
                            + "\u0000"
            );
        }

        private void sendReceipt(Map<String, String> headers) {
            String receiptId = headers.get("receipt");
            if (receiptId != null && !receiptId.isBlank()) {
                outgoing.tryEmitNext("RECEIPT\nreceipt-id:" + receiptId + "\n\n\u0000");
            }
        }

        private void sendError(String message) {
            outgoing.tryEmitNext(
                    "ERROR\n"
                            + "message:" + message.replace("\n", " ") + "\n"
                            + "content-type:text/plain\n"
                            + "\n"
                            + message
                            + "\u0000"
            );
        }

        private String toJson(Object payload) {
            try {
                return objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException exception) {
                return null;
            }
        }

        private void dispose() {
            subscriptions.values().forEach(Disposable::dispose);
            subscriptions.clear();
        }
    }
}
