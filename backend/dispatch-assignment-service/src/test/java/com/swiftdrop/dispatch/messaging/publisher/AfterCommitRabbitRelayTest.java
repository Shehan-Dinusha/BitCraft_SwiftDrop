package com.swiftdrop.dispatch.messaging.publisher;

import com.swiftdrop.dispatch.config.RabbitMQConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AfterCommitRabbitRelayTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final AfterCommitRabbitRelay relay = new AfterCommitRabbitRelay(rabbitTemplate);

    @Test
    @DisplayName("Relays the message to the events exchange with its routing key")
    void relaysToEventsExchange() {
        Object payload = new Object();

        relay.relay(new OutboundDispatchMessage(RabbitMQConfig.ROUTING_KEY_COURIER_ASSIGNED, payload, UUID.randomUUID()));

        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY_COURIER_ASSIGNED, payload);
    }

    @Test
    @DisplayName("A broker failure after commit is logged, not propagated to the committed caller")
    void brokerFailureIsNotPropagated() {
        doThrow(new AmqpConnectException(new ConnectException("broker down")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> relay.relay(new OutboundDispatchMessage(
                RabbitMQConfig.ROUTING_KEY_COURIER_ASSIGNED, new Object(), UUID.randomUUID())))
                .doesNotThrowAnyException();
    }
}
