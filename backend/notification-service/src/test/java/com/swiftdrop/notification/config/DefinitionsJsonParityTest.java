package com.swiftdrop.notification.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Parses {@code infra/local/rabbitmq/definitions.json} and asserts that every
 * queue and binding declared in the Java {@code RabbitMQConfig} classes exists.
 *
 * <p>Removing a required queue or binding from definitions.json will make this test fail,
 * preventing silent configuration drift.</p>
 */
class DefinitionsJsonParityTest {

    private static JsonNode root;

    /** Required queues that must exist in definitions.json. */
    private static final List<String> REQUIRED_QUEUES = List.of(
            // Order
            "order.events.queue",
            "dlq.order.events.queue",
            "customer-order.fee-revision.queue",
            // Dispatch
            "dispatch.events.queue",
            "dispatch.order-created.queue",
            "dispatch.order-cancelled.queue",
            "dispatch.parcel-status.queue",
            "dlq.dispatch.events.queue",
            // Delivery
            "delivery.events.queue",
            "delivery.dispatch-assigned.queue",
            "dlq.delivery.events.queue",
            // Notification
            "notification.events.queue",
            "dlq.notification.events.queue",
            // Webhook
            "webhook.events.queue",
            "dlq.webhook.events.queue",
            // Analytics
            "analytics.events.queue",
            "dlq.analytics.events.queue",
            // Gateway
            "swiftdrop.gateway.tracking.queue",
            "swiftdrop.gateway.fee.queue",
            // Auth / IAM
            "swiftdrop.auth.events",
            "iam.merchant-approved.queue"
    );

    /** Required bindings as (destination queue, routing key) pairs on swiftdrop.events.exchange. */
    private static final List<String[]> REQUIRED_BINDINGS = List.of(
            new String[]{"order.events.queue", "order.#"},
            new String[]{"customer-order.fee-revision.queue", "parcel.fee.revised"},
            new String[]{"dispatch.events.queue", "dispatch.#"},
            new String[]{"dispatch.order-created.queue", "order.created"},
            new String[]{"dispatch.order-cancelled.queue", "order.cancelled"},
            new String[]{"dispatch.parcel-status.queue", "parcel.status.changed.#"},
            new String[]{"delivery.events.queue", "delivery.#"},
            new String[]{"delivery.dispatch-assigned.queue", "dispatch.courier.assigned"},
            new String[]{"notification.events.queue", "notification.#"},
            new String[]{"notification.events.queue", "parcel.status.changed.#"},
            new String[]{"notification.events.queue", "order.created"},
            new String[]{"webhook.events.queue", "parcel.status.changed.#"},
            new String[]{"webhook.events.queue", "order.created"},
            new String[]{"webhook.events.queue", "webhook.dispatch.requested"},
            new String[]{"swiftdrop.gateway.tracking.queue", "parcel.status.changed.#"},
            new String[]{"swiftdrop.gateway.fee.queue", "parcel.fee.revised"},
            new String[]{"analytics.events.queue", "parcel.status.changed.#"},
            new String[]{"analytics.events.queue", "dispatch.#"},
            new String[]{"swiftdrop.auth.events", "auth.#"},
            new String[]{"iam.merchant-approved.queue", "merchant.approved"}
    );

    @BeforeAll
    static void loadDefinitions() throws Exception {
        // The file lives at project root: infra/local/rabbitmq/definitions.json
        // We walk up from notification-service/target/test-classes to find it.
        var path = java.nio.file.Path.of("").toAbsolutePath();
        // Navigate to repo root: could be running from backend/ or from backend/notification-service/
        while (path != null && !java.nio.file.Files.exists(path.resolve("infra/local/rabbitmq/definitions.json"))) {
            path = path.getParent();
        }
        if (path == null) {
            fail("Could not locate infra/local/rabbitmq/definitions.json from " + java.nio.file.Path.of("").toAbsolutePath());
        }
        try (InputStream is = java.nio.file.Files.newInputStream(path.resolve("infra/local/rabbitmq/definitions.json"))) {
            root = new ObjectMapper().readTree(is);
        }
    }

    @Test
    @DisplayName("definitions.json contains all required queues")
    void allRequiredQueuesExist() {
        JsonNode queues = root.get("queues");
        List<String> queueNames = new ArrayList<>();
        queues.forEach(q -> queueNames.add(q.get("name").asText()));

        List<String> missing = REQUIRED_QUEUES.stream()
                .filter(rq -> !queueNames.contains(rq))
                .toList();

        assertTrue(missing.isEmpty(),
                "definitions.json is missing required queues: " + missing);
    }

    @Test
    @DisplayName("definitions.json contains all required bindings")
    void allRequiredBindingsExist() {
        JsonNode bindings = root.get("bindings");

        List<String> missing = new ArrayList<>();
        for (String[] required : REQUIRED_BINDINGS) {
            String dest = required[0];
            String routingKey = required[1];
            boolean found = false;
            for (JsonNode b : bindings) {
                if (dest.equals(b.get("destination").asText())
                        && routingKey.equals(b.get("routing_key").asText())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                missing.add(dest + " ← " + routingKey);
            }
        }

        assertTrue(missing.isEmpty(),
                "definitions.json is missing required bindings: " + missing);
    }

    @Test
    @DisplayName("definitions.json queues list is not empty")
    void queuesNotEmpty() {
        assertFalse(root.get("queues").isEmpty(), "No queues defined in definitions.json");
    }

    @Test
    @DisplayName("definitions.json bindings list is not empty")
    void bindingsNotEmpty() {
        assertFalse(root.get("bindings").isEmpty(), "No bindings defined in definitions.json");
    }
}
