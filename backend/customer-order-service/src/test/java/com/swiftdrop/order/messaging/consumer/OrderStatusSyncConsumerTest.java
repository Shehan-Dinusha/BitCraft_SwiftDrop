package com.swiftdrop.order.messaging.consumer;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.order.service.OrderStatusSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatusSyncConsumerTest {

    @Mock
    private OrderStatusSyncService service;

    @InjectMocks
    private OrderStatusSyncConsumer consumer;

    private ParcelStatusChangedEvent event() {
        return new ParcelStatusChangedEvent(UUID.randomUUID(), UUID.randomUUID(), "SD-7K4M-9QX2", UUID.randomUUID(), UUID.randomUUID(),
                PublicOrderStatus.PICKED_UP, PublicOrderStatus.IN_TRANSIT, null, null, null, null, null);
    }

    @Test
    @DisplayName("Every skip outcome is acknowledged normally, so a skipped message never loops")
    void skipOutcomesDoNotThrow() {
        for (OrderStatusSyncService.Outcome outcome : OrderStatusSyncService.Outcome.values()) {
            when(service.apply(any())).thenReturn(outcome);

            assertThatCode(() -> consumer.onParcelStatusChanged(event())).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("A real failure (such as the database being down) propagates so retry and dead-lettering apply")
    void genuineFailuresPropagate() {
        ParcelStatusChangedEvent event = event();
        when(service.apply(event)).thenThrow(new DataAccessResourceFailureException("database down"));

        assertThatThrownBy(() -> consumer.onParcelStatusChanged(event)).isInstanceOf(DataAccessResourceFailureException.class);
        verify(service).apply(event);
    }
}
