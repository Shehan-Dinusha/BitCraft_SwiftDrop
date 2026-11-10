package com.swiftdrop.order.service;

import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import com.swiftdrop.order.domain.entity.OrderStatusHistory;
import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import com.swiftdrop.order.domain.repository.OrderStatusHistoryRepository;
import com.swiftdrop.order.domain.repository.ProcessedEventRepository;
import com.swiftdrop.order.service.OrderStatusSyncService.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatusSyncServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-01T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-01T09:00:00Z");

    @Mock
    private CustomerOrderRepository customerOrderRepository;
    @Mock
    private OrderStatusHistoryRepository historyRepository;
    @Mock
    private OrderStatusHistoryService orderStatusHistoryService;
    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private OrderStatusSyncService service;

    private UUID orderId;
    private CustomerOrder order;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        order = CustomerOrder.builder()
                .id(1L).orderId(orderId).trackingNumber("SD-7K4M-9QX2").customerId(UUID.randomUUID())
                .status(PublicOrderStatus.PICKED_UP).internalQueueStatus(InternalQueueStatus.PICKED_UP)
                .createdAt(T0).updatedAt(T0).build();
    }

    private ParcelStatusChangedEvent event(PublicOrderStatus from, PublicOrderStatus to, InternalQueueStatus sub, Instant at) {
        return new ParcelStatusChangedEvent(UUID.randomUUID(), UUID.randomUUID(), "SD-7K4M-9QX2", orderId, order.getCustomerId(),
                from, to, sub, SriLankanDistrict.COLOMBO, SriLankanDistrict.KANDY, null, at);
    }

    private void claimed() {
        when(processedEventRepository.claim(any())).thenReturn(1);
    }

    private void orderFound() {
        when(customerOrderRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(historyRepository.findFirstByOrderIdAndEventIdIsNotNullOrderByOccurredAtDescIdDesc(orderId)).thenReturn(Optional.of(
                OrderStatusHistory.builder().orderId(orderId).status(PublicOrderStatus.PICKED_UP).eventId(UUID.randomUUID()).occurredAt(T0).build()));
    }

    @Test
    @DisplayName("A newer status is applied: order updated, internal status set, history row written with the event id and time")
    void appliesNewStatus() {
        claimed();
        orderFound();
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        ParcelStatusChangedEvent event = event(PublicOrderStatus.PICKED_UP, PublicOrderStatus.IN_TRANSIT, InternalQueueStatus.OUTBOUND, T1);

        Outcome outcome = service.apply(event);

        assertThat(outcome).isEqualTo(Outcome.APPLIED);
        assertThat(order.getStatus()).isEqualTo(PublicOrderStatus.IN_TRANSIT);
        assertThat(order.getInternalQueueStatus()).isEqualTo(InternalQueueStatus.OUTBOUND);
        verify(customerOrderRepository).save(order);
        verify(orderStatusHistoryService).recordStatus(orderId, PublicOrderStatus.IN_TRANSIT, InternalQueueStatus.OUTBOUND, event.eventId(), T1);
        verify(customerOrderRepository).findByOrderIdForUpdate(orderId);
        verify(customerOrderRepository, never()).findByOrderId(any());
    }

    @Test
    @DisplayName("A duplicate event id is ignored and changes nothing")
    void duplicateEventIsIgnored() {
        when(customerOrderRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(processedEventRepository.claim(any())).thenReturn(0);

        Outcome outcome = service.apply(event(PublicOrderStatus.PICKED_UP, PublicOrderStatus.IN_TRANSIT, null, T1));

        assertThat(outcome).isEqualTo(Outcome.DUPLICATE_EVENT);
        verify(customerOrderRepository, never()).save(any());
        verifyNoInteractions(orderStatusHistoryService, historyRepository);
    }

    @Test
    @DisplayName("An unknown order is skipped without throwing, after trying the tracking number, and the event id is not claimed")
    void unknownOrderIsSkipped() {
        when(customerOrderRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.empty());
        when(customerOrderRepository.findByTrackingNumber("SD-7K4M-9QX2")).thenReturn(Optional.empty());

        Outcome outcome = service.apply(event(PublicOrderStatus.PICKED_UP, PublicOrderStatus.IN_TRANSIT, null, T1));

        assertThat(outcome).isEqualTo(Outcome.UNKNOWN_ORDER);
        verifyNoInteractions(processedEventRepository, orderStatusHistoryService);
        verify(customerOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("A redelivered event for an order that was unknown is processed once the order exists")
    void unknownOrderEventIsRetried() {
        ParcelStatusChangedEvent event = event(PublicOrderStatus.PICKED_UP, PublicOrderStatus.IN_TRANSIT, null, T1);
        when(customerOrderRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.empty(), Optional.of(order));
        when(customerOrderRepository.findByTrackingNumber("SD-7K4M-9QX2")).thenReturn(Optional.empty());
        when(processedEventRepository.claim(event.eventId())).thenReturn(1);
        when(historyRepository.findFirstByOrderIdAndEventIdIsNotNullOrderByOccurredAtDescIdDesc(orderId)).thenReturn(Optional.empty());
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.apply(event)).isEqualTo(Outcome.UNKNOWN_ORDER);
        assertThat(service.apply(event)).isEqualTo(Outcome.APPLIED);
    }

    @Test
    @DisplayName("The order is found by tracking number when the order id is unknown, and locked by its id")
    void fallsBackToTrackingNumber() {
        claimed();
        when(customerOrderRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.empty(), Optional.of(order));
        when(customerOrderRepository.findByTrackingNumber("SD-7K4M-9QX2")).thenReturn(Optional.of(order));
        when(historyRepository.findFirstByOrderIdAndEventIdIsNotNullOrderByOccurredAtDescIdDesc(orderId)).thenReturn(Optional.empty());
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.apply(event(PublicOrderStatus.PICKED_UP, PublicOrderStatus.IN_TRANSIT, null, T1)))
                .isEqualTo(Outcome.APPLIED);
    }

    @Test
    @DisplayName("The same status is ignored: this is how the order service's own create and cancel events are skipped")
    void sameStatusIsIgnored() {
        claimed();
        orderFound();
        order.setStatus(PublicOrderStatus.CREATED);
        order.setInternalQueueStatus(InternalQueueStatus.CREATED);

        Outcome outcome = service.apply(event(null, PublicOrderStatus.CREATED, InternalQueueStatus.CREATED, T1));

        assertThat(outcome).isEqualTo(Outcome.SAME_STATUS);
        verify(customerOrderRepository, never()).save(any());
        verifyNoInteractions(orderStatusHistoryService);
    }

    @Test
    @DisplayName("The same public status with a new internal status only updates the internal status, with no history row")
    void sameStatusNewSubStatusUpdatesInternalOnly() {
        claimed();
        orderFound();
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        Outcome outcome = service.apply(event(PublicOrderStatus.PICKED_UP, PublicOrderStatus.PICKED_UP, InternalQueueStatus.AT_HUB, T1));

        assertThat(outcome).isEqualTo(Outcome.SUB_STATUS_UPDATED);
        assertThat(order.getStatus()).isEqualTo(PublicOrderStatus.PICKED_UP);
        assertThat(order.getInternalQueueStatus()).isEqualTo(InternalQueueStatus.AT_HUB);
        verifyNoInteractions(orderStatusHistoryService);
    }

    @ParameterizedTest(name = "[{index}] {0} is final")
    @EnumSource(value = PublicOrderStatus.class, names = {"DELIVERED", "CANCELLED", "RETURNED_TO_SENDER"})
    void finalStatusesAreProtected(PublicOrderStatus finalStatus) {
        claimed();
        orderFound();
        order.setStatus(finalStatus);

        Outcome outcome = service.apply(event(finalStatus, PublicOrderStatus.IN_TRANSIT, null, T1));

        assertThat(outcome).isEqualTo(Outcome.FINAL_STATUS);
        assertThat(order.getStatus()).isEqualTo(finalStatus);
        verify(customerOrderRepository, never()).save(any());
        verifyNoInteractions(orderStatusHistoryService);
    }

    @Test
    @DisplayName("A failed delivery is retried: FAILED can move on to out-for-delivery")
    void failedCanMoveOn() {
        claimed();
        orderFound();
        order.setStatus(PublicOrderStatus.FAILED);
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        Outcome outcome = service.apply(event(PublicOrderStatus.FAILED, PublicOrderStatus.OUT_FOR_DELIVERY, null, T1));

        assertThat(outcome).isEqualTo(Outcome.APPLIED);
        assertThat(order.getStatus()).isEqualTo(PublicOrderStatus.OUT_FOR_DELIVERY);
    }

    @Test
    @DisplayName("An event older than the latest event-sourced history entry is skipped")
    void outOfOrderEventIsSkipped() {
        claimed();
        when(customerOrderRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(historyRepository.findFirstByOrderIdAndEventIdIsNotNullOrderByOccurredAtDescIdDesc(orderId)).thenReturn(Optional.of(
                OrderStatusHistory.builder().orderId(orderId).status(PublicOrderStatus.OUT_FOR_DELIVERY).eventId(UUID.randomUUID()).occurredAt(T1).build()));

        Outcome outcome = service.apply(event(PublicOrderStatus.CREATED, PublicOrderStatus.IN_TRANSIT, null, T0));

        assertThat(outcome).isEqualTo(Outcome.OUT_OF_ORDER);
        assertThat(order.getStatus()).isEqualTo(PublicOrderStatus.PICKED_UP);
        verify(customerOrderRepository, never()).save(any());
        verifyNoInteractions(orderStatusHistoryService);
    }

    @Test
    @DisplayName("An event older than this service's own CREATED row but newer than any event is applied")
    void eventOlderThanOwnRowButNewerThanLastEventIsApplied() {
        claimed();
        order.setStatus(PublicOrderStatus.CREATED);
        order.setInternalQueueStatus(InternalQueueStatus.CREATED);
        when(customerOrderRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(order));
        // no event-sourced row exists; our own CREATED row (stamped later than the event) must not count
        when(historyRepository.findFirstByOrderIdAndEventIdIsNotNullOrderByOccurredAtDescIdDesc(orderId)).thenReturn(Optional.empty());
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        Outcome outcome = service.apply(event(PublicOrderStatus.CREATED, PublicOrderStatus.PICKED_UP, InternalQueueStatus.PICKED_UP, T0));

        assertThat(outcome).isEqualTo(Outcome.APPLIED);
        assertThat(order.getStatus()).isEqualTo(PublicOrderStatus.PICKED_UP);
        verify(historyRepository, never()).findFirstByOrderIdOrderByOccurredAtDescIdDesc(any());
    }

    @Test
    @DisplayName("An event older than this service's own CANCELLED row is not judged against it: the final-status rule decides")
    void ownCancelledRowIsIgnoredByOrderGuard() {
        claimed();
        order.setStatus(PublicOrderStatus.CANCELLED);
        when(customerOrderRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(historyRepository.findFirstByOrderIdAndEventIdIsNotNullOrderByOccurredAtDescIdDesc(orderId)).thenReturn(Optional.empty());

        Outcome outcome = service.apply(event(PublicOrderStatus.PICKED_UP, PublicOrderStatus.IN_TRANSIT, null, T0));

        assertThat(outcome).isEqualTo(Outcome.FINAL_STATUS);
        verify(customerOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("An event with no public status is ignored")
    void eventWithoutStatusIsIgnored() {
        claimed();

        Outcome outcome = service.apply(event(PublicOrderStatus.PICKED_UP, null, null, T1));

        assertThat(outcome).isEqualTo(Outcome.NO_STATUS);
        verifyNoInteractions(customerOrderRepository, orderStatusHistoryService);
    }

    @Test
    @DisplayName("A cancellation event sets the cancelled time and the internal status")
    void cancellationEventSetsCancelledTime() {
        claimed();
        orderFound();
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        Outcome outcome = service.apply(event(PublicOrderStatus.PICKED_UP, PublicOrderStatus.CANCELLED, InternalQueueStatus.CANCELLED, T1));

        assertThat(outcome).isEqualTo(Outcome.APPLIED);
        assertThat(order.getCancelledAt()).isEqualTo(T1);
        verify(orderStatusHistoryService).recordStatus(eq(orderId), eq(PublicOrderStatus.CANCELLED), eq(InternalQueueStatus.CANCELLED), any(), eq(T1));
    }
}
