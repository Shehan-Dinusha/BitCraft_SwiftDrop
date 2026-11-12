package com.swiftdrop.analytics.ai;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ChatSessionStoreTest {
    @Test void conversationsExpireAndRateWindowResets() {
        Clock clock = org.mockito.Mockito.mock(Clock.class);
        var now = Instant.parse("2026-11-10T00:00:00Z");
        org.mockito.Mockito.when(clock.instant()).thenReturn(now);
        var store = new ChatSessionStore(clock);
        var user = UUID.randomUUID();
        var session = store.acquire(user, null);
        for (int i = 0; i < 9; i++) store.acquire(user, null);
        org.mockito.Mockito.when(clock.instant()).thenReturn(now.plusSeconds(61));
        assertThatCode(() -> store.acquire(user, session.id)).doesNotThrowAnyException();
        org.mockito.Mockito.when(clock.instant()).thenReturn(now.plusSeconds(2000));
        assertThatThrownBy(() -> store.acquire(user, session.id)).hasMessageContaining("404");
    }
}
