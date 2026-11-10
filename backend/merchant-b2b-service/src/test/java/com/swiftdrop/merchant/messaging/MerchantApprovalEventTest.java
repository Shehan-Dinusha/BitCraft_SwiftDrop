package com.swiftdrop.merchant.messaging;

import com.swiftdrop.merchant.config.RabbitMQConfig;
import com.swiftdrop.merchant.messaging.event.MerchantApprovedEvent;
import com.swiftdrop.merchant.messaging.publisher.MerchantEventPublisher;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(MerchantApprovalEventTest.Config.class)
class MerchantApprovalEventTest {
    @Autowired ApplicationEventPublisher events;
    @Autowired RabbitTemplate rabbit;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void clearMock() { reset(rabbit); }

    @Test void publishesOnlyAfterCommit() {
        var event = event();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            events.publishEvent(event);
            verifyNoInteractions(rabbit);
        });
        verify(rabbit).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(MerchantApprovedEvent.ROUTING_KEY), eq(event), any(MessagePostProcessor.class));
    }

    @Test void rollbackDoesNotPublish() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            events.publishEvent(event());
            status.setRollbackOnly();
        });
        verifyNoInteractions(rabbit);
    }

    private MerchantApprovedEvent event() {
        return new MerchantApprovedEvent(null, null, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null);
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean RabbitTemplate rabbit() { return mock(RabbitTemplate.class); }
        @Bean MerchantEventPublisher publisher(RabbitTemplate rabbit) { return new MerchantEventPublisher(rabbit); }
        @Bean PlatformTransactionManager transactions() {
            return new AbstractPlatformTransactionManager() {
                protected Object doGetTransaction() { return new Object(); }
                protected void doBegin(Object transaction, TransactionDefinition definition) {}
                protected void doCommit(DefaultTransactionStatus status) {}
                protected void doRollback(DefaultTransactionStatus status) {}
            };
        }
    }
}
