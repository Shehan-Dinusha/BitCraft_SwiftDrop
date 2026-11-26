package com.swiftdrop.notification.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * The recipient's email for their parcel updates, from customer-order-service
 * ({@code GET /internal/v1/orders/{id}/recipient-contact}). Events never carry an email address, so it is
 * asked for each time a parcel moves; nothing is cached or stored here.
 */
@Slf4j
@Component
public class OrderRecipientClient {

    private static final String PATH = "/internal/v1/orders/{id}/recipient-contact";

    private final RestClient restClient;

    @Autowired
    public OrderRecipientClient(
            RestClient.Builder restClientBuilder,
            @Value("${notification.orders.base-url:http://localhost:8082}") String orderBaseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = restClientBuilder.clone().baseUrl(orderBaseUrl).requestFactory(requestFactory).build();
    }

    public OrderRecipientClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Who the parcel goes to, or empty when the order is unknown or the order service cannot be reached. */
    public Optional<RecipientContact> lookup(UUID orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        try {
            JsonNode body = restClient.get().uri(PATH, orderId).retrieve().body(JsonNode.class);
            JsonNode data = body == null ? null : body.hasNonNull("data") ? body.get("data") : body;
            if (data == null || !data.isObject()) {
                return Optional.empty();
            }
            return Optional.of(new RecipientContact(
                    text(data, "trackingNumber"),
                    text(data, "recipientEmail"),
                    text(data, "recipientName"),
                    text(data, "senderName"),
                    text(data, "deliverBy")));
        } catch (HttpClientErrorException e) {
            log.debug("No recipient contact for order {}: {}", orderId, e.getStatusCode());
            return Optional.empty();
        } catch (RestClientException e) {
            // The order service is down or slow: the recipient misses this update, the sender's mail still goes.
            log.warn("Recipient contact lookup for order {} failed: {}", orderId, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            return null;
        }
        String value = node.get(field).asText().trim();
        return value.isEmpty() ? null : value;
    }

    /** The recipient's email (null when the sender left it out) and the names their emails show. */
    public record RecipientContact(String trackingNumber, String email, String recipientName, String senderName,
                                   String deliverBy) {
    }
}
