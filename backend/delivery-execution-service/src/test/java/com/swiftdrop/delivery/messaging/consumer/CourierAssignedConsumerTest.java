package com.swiftdrop.delivery.messaging.consumer;

import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.delivery.service.DeliveryExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CourierAssignedConsumerTest {

    private final DeliveryExecutionService service = mock(DeliveryExecutionService.class);
    private final CourierAssignedConsumer consumer = new CourierAssignedConsumer(service);
    private final CourierAssignedEvent event = mock(CourierAssignedEvent.class);

    @Test
    void handsTheEventToTheService() {
        consumer.onCourierAssigned(event);
        verify(service).handleCourierAssigned(event);
    }

    /**
     * The original exception must reach the listener container unchanged: the retry classifier
     * decides from its type whether to retry, and swallowing it would ack and lose the assignment.
     */
    @Test
    void rethrowsTheOriginalFailure() {
        TransientDataAccessResourceException outage = new TransientDataAccessResourceException("db down");
        doThrow(outage).when(service).handleCourierAssigned(any());

        assertThatThrownBy(() -> consumer.onCourierAssigned(event)).isSameAs(outage);
    }
}
