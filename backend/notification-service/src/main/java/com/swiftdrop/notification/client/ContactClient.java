package com.swiftdrop.notification.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.notification.dto.RecipientContact;
import com.swiftdrop.notification.util.PiiMasker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves recipient contact details (email and phone) from auth-iam-service
 * ({@code GET /internal/v1/users/{id}/contact}) without carrying PII on domain events.
 * Includes a 10-minute in-memory cache and a deterministic fake fallback when {@code CONTACT_MODE=fake}.
 */
@Slf4j
@Component
public class ContactClient {

    private static final String INTERNAL_CONTACT_PATH = "/internal/v1/users/{id}/contact";

    private final RestClient restClient;
    private final String contactMode;
    private final Duration cacheTtl;
    private final Clock clock;
    private final Map<UUID, CachedContact> cache = new ConcurrentHashMap<>();

    @Autowired
    public ContactClient(
            RestClient.Builder restClientBuilder,
            @Value("${notification.contact.mode:iam}") String contactMode,
            @Value("${notification.contact.iam-base-url:http://localhost:8081}") String iamBaseUrl,
            @Value("${notification.contact.cache-ttl-minutes:10}") long cacheTtlMinutes
    ) {
        this(
                configureBuilder(restClientBuilder, iamBaseUrl).build(),
                contactMode,
                Duration.ofMinutes(cacheTtlMinutes),
                Clock.systemUTC()
        );
    }

    public ContactClient(RestClient restClient, String contactMode, Duration cacheTtl, Clock clock) {
        this.restClient = restClient;
        this.contactMode = contactMode != null ? contactMode : "iam";
        this.cacheTtl = cacheTtl != null ? cacheTtl : Duration.ofMinutes(10);
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    private static RestClient.Builder configureBuilder(RestClient.Builder builder, String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return builder.clone().baseUrl(baseUrl).requestFactory(requestFactory);
    }

    /**
     * Looks up contact coordinates for the given user UUID using cache, fake mode, or IAM HTTP lookup.
     */
    public Optional<RecipientContact> lookupContact(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        CachedContact cached = cache.get(userId);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            log.debug("Contact cache hit for user {}: email={}, phone={}",
                    userId, PiiMasker.maskEmail(cached.contact().email()), PiiMasker.maskPhone(cached.contact().phone()));
            return Optional.of(cached.contact());
        }

        Optional<RecipientContact> resolved = "fake".equalsIgnoreCase(contactMode)
                ? Optional.of(generateFakeContact(userId))
                : fetchFromIam(userId);

        resolved.ifPresent(contact -> {
            cache.put(userId, new CachedContact(contact, now.plus(cacheTtl)));
            log.info("Resolved recipient contact for user {} (mode={}): email={}, phone={}",
                    userId, contactMode, PiiMasker.maskEmail(contact.email()), PiiMasker.maskPhone(contact.phone()));
        });

        return resolved;
    }

    private Optional<RecipientContact> fetchFromIam(UUID userId) {
        try {
            JsonNode body = restClient.get()
                    .uri(INTERNAL_CONTACT_PATH, userId)
                    .retrieve()
                    .body(JsonNode.class);

            if (body == null || body.isNull()) {
                return Optional.empty();
            }

            JsonNode payload = body.hasNonNull("data") && body.get("data").isObject()
                    ? body.get("data")
                    : body;

            String email = readText(payload, "email", "emailAddress");
            String phone = readText(payload, "phone", "phoneNumber");

            if (email == null && phone == null) {
                return Optional.empty();
            }
            return Optional.of(new RecipientContact(email, phone));
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.debug("No IAM contact found for user {}", userId);
            } else {
                log.warn("IAM contact lookup failed for user {} with status {}", userId, ex.getStatusCode());
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("IAM contact lookup failed for user {}: {}", userId, ex.getMessage());
            return Optional.empty();
        }
    }

    private RecipientContact generateFakeContact(UUID userId) {
        String shortId = userId.toString().substring(0, 8);
        long numericTail = Math.abs(userId.getLeastSignificantBits() % 10_000_000L);
        String phone = String.format("+9477%07d", numericTail);
        String email = "user-" + shortId + "@swiftdrop.example";
        return new RecipientContact(email, phone);
    }

    private static String readText(JsonNode node, String primaryField, String fallbackField) {
        if (node.hasNonNull(primaryField)) {
            String value = node.get(primaryField).asText();
            if (!value.isBlank()) {
                return value;
            }
        }
        if (node.hasNonNull(fallbackField)) {
            String value = node.get(fallbackField).asText();
            if (!value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record CachedContact(RecipientContact contact, Instant expiresAt) {
    }
}
