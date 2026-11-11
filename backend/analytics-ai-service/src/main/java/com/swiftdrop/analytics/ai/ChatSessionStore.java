package com.swiftdrop.analytics.ai;

import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

/** Single-instance, ephemeral storage. Only redacted text is retained. */
@Component
public class ChatSessionStore {
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Window> limits = new HashMap<>();
    private final Clock clock;
    public ChatSessionStore() { this(Clock.systemUTC()); }
    ChatSessionStore(Clock clock) { this.clock = clock; }

    public synchronized Session acquire(UUID owner, UUID id) {
        Instant now = clock.instant();
        sessions.values().removeIf(s -> s.lastUsed.isBefore(now.minusSeconds(1800)));
        limits.values().removeIf(w -> w.start.isBefore(now.minusSeconds(60)));
        var window = limits.get(owner);
        if (window == null) {
            if (limits.size() >= 10000) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Assistant capacity reached");
            window = new Window(now); limits.put(owner, window);
        }
        if (++window.count > 10) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Limit is 10 messages per minute; retry in 60 seconds");
        if (id != null) {
            var session = sessions.get(id);
            if (session == null || !session.owner.equals(owner)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found or expired");
            session.lastUsed = now;
            return session;
        }
        if (sessions.size() >= 1000) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Assistant conversation capacity reached");
        var session = new Session(owner, now);
        sessions.put(session.id, session);
        return session;
    }

    public static final class Session {
        final UUID id = UUID.randomUUID();
        final UUID owner;
        volatile Instant lastUsed;
        final List<Map<String, Object>> history = new ArrayList<>();
        Session(UUID owner, Instant now) { this.owner = owner; this.lastUsed = now; }
    }
    private static final class Window {
        final Instant start; int count;
        Window(Instant start) { this.start = start; }
    }
}
