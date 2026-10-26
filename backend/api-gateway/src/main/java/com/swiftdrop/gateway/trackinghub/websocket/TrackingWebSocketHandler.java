package com.swiftdrop.gateway.trackinghub.websocket;

import com.swiftdrop.gateway.trackinghub.controller.LiveTrackingStreamController;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;

/**
 * Routes {@code /ws/tracking} to the STOMP handler when the client requests the
 * {@code stomp} subprotocol, and to the legacy raw JSON stream otherwise. This
 * lets the existing frontend migrate without a flag day.
 */
@Component
public class TrackingWebSocketHandler implements WebSocketHandler {

    private final LiveTrackingStreamController legacyHandler;
    private final StompTrackingWebSocketHandler stompHandler;

    public TrackingWebSocketHandler(LiveTrackingStreamController legacyHandler,
                                    StompTrackingWebSocketHandler stompHandler) {
        this.legacyHandler = legacyHandler;
        this.stompHandler = stompHandler;
    }

    @Override
    public List<String> getSubProtocols() {
        return List.of("stomp", "v12.stomp", "v11.stomp", "v10.stomp");
    }

    @Override
    @NonNull
    public Mono<Void> handle(@NonNull WebSocketSession session) {
        String subProtocol = session.getHandshakeInfo().getSubProtocol();
        boolean stompRequested = subProtocol != null
                && subProtocol.toLowerCase(Locale.ROOT).contains("stomp");
        return stompRequested
                ? stompHandler.handle(session)
                : legacyHandler.handle(session);
    }
}
