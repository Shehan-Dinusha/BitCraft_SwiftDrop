package com.swiftdrop.analytics.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQConfigTest {
    @Test
    void javaQueueArgumentsAndBindingsMatchSharedBrokerDefinitions() throws Exception {
        JsonNode definitions = new ObjectMapper().readTree(Path.of("../../infra/local/rabbitmq/definitions.json").toFile());
        RabbitMQConfig config = new RabbitMQConfig();
        for (Queue queue : new Queue[]{config.analyticsEventsQueue(), config.dlqAnalyticsEventsQueue()}) {
            JsonNode broker = null;
            for (JsonNode candidate : definitions.get("queues")) {
                if (candidate.get("name").asText().equals(queue.getName())) broker = candidate;
            }
            assertThat(broker).as(queue.getName()).isNotNull();
            assertThat(queue.isDurable()).isEqualTo(broker.get("durable").asBoolean());
            assertThat(queue.isAutoDelete()).isEqualTo(broker.get("auto_delete").asBoolean());
            JsonNode javaArguments = new ObjectMapper().valueToTree(queue.getArguments());
            assertThat(javaArguments).isEqualTo(broker.get("arguments"));
        }
        var exchange = config.eventsExchange();
        var queue = config.analyticsEventsQueue();
        for (var binding : new org.springframework.amqp.core.Binding[]{
                config.bindAnalyticsEventsParcel(queue, exchange),
                config.bindAnalyticsEventsDispatch(queue, exchange),
                config.bindAnalyticsOrderCreated(queue, exchange),
                config.bindDlqAnalyticsEvents(config.dlqAnalyticsEventsQueue(), config.dlxExchange())}) {
            boolean found = false;
            for (JsonNode candidate : definitions.get("bindings")) {
                found |= candidate.get("source").asText().equals(binding.getExchange())
                        && candidate.get("destination").asText().equals(binding.getDestination())
                        && candidate.get("routing_key").asText().equals(binding.getRoutingKey());
            }
            assertThat(found).as(binding.getDestination() + ":" + binding.getRoutingKey()).isTrue();
        }
    }
}
