package com.swiftdrop.notification.client;

import com.swiftdrop.notification.dto.RecipientContact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ContactClientTest {

    private static class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void advanceBy(Duration duration) {
            current.updateAndGet(t -> t.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }

    @Test
    @DisplayName("Should fetch contact from IAM /internal/v1/users/{id}/contact and cache for 10 minutes")
    void testIamLookupAndCaching() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8081");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        MutableClock clock = new MutableClock(Instant.parse("2026-11-10T10:00:00Z"));
        ContactClient client = new ContactClient(builder.build(), "iam", Duration.ofMinutes(10), clock);

        UUID userId = UUID.randomUUID();
        String expectedUri = "http://localhost:8081/internal/v1/users/" + userId + "/contact";

        server.expect(requestTo(expectedUri))
                .andRespond(withSuccess("""
                        {
                            "success": true,
                            "data": {
                                "email": "alice@gmail.com",
                                "phone": "+94771234567"
                            }
                        }
                        """, MediaType.APPLICATION_JSON));

        // 1st call -> hits IAM
        Optional<RecipientContact> first = client.lookupContact(userId);
        assertThat(first).isPresent();
        assertThat(first.get().email()).isEqualTo("alice@gmail.com");
        assertThat(first.get().phone()).isEqualTo("+94771234567");

        // 5 minutes later -> served from cache (no second HTTP call)
        clock.advanceBy(Duration.ofMinutes(5));
        Optional<RecipientContact> cached = client.lookupContact(userId);
        assertThat(cached).isPresent().contains(first.get());
        server.verify();

        // Advance past 10-minute TTL -> refreshes from IAM
        server.reset();
        server.expect(requestTo(expectedUri))
                .andRespond(withSuccess("""
                        {
                            "email": "alice.new@gmail.com",
                            "phone": "0779998888"
                        }
                        """, MediaType.APPLICATION_JSON));

        clock.advanceBy(Duration.ofMinutes(6));
        Optional<RecipientContact> refreshed = client.lookupContact(userId);
        assertThat(refreshed).isPresent();
        assertThat(refreshed.get().email()).isEqualTo("alice.new@gmail.com");
        assertThat(refreshed.get().phone()).isEqualTo("0779998888");
        server.verify();
    }

    @Test
    @DisplayName("Should return empty when IAM returns 404 Not Found")
    void testIamNotFoundReturnsEmpty() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8081");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        ContactClient client = new ContactClient(builder.build(), "iam", Duration.ofMinutes(10), Clock.systemUTC());
        UUID userId = UUID.randomUUID();

        server.expect(requestTo("http://localhost:8081/internal/v1/users/" + userId + "/contact"))
                .andRespond(withResourceNotFound());

        Optional<RecipientContact> result = client.lookupContact(userId);
        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Should generate deterministic fake contact without HTTP call when CONTACT_MODE=fake")
    void testFakeModeFallback() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8081");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        ContactClient client = new ContactClient(builder.build(), "fake", Duration.ofMinutes(10), Clock.systemUTC());
        UUID userId = UUID.fromString("12345678-aaaa-bbbb-cccc-123456789012");

        Optional<RecipientContact> result = client.lookupContact(userId);
        assertThat(result).isPresent();
        assertThat(result.get().email()).isEqualTo("user-12345678@swiftdrop.example");
        assertThat(result.get().phone()).startsWith("+9477");

        // Ensure zero HTTP calls were made
        server.verify();
    }
}
