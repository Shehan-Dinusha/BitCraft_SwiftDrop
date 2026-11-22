package com.swiftdrop.delivery.service;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.delivery.client.OrderJobClient;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.repository.DeliverySessionRepository;
import com.swiftdrop.delivery.dto.CourierJobResponse;
import com.swiftdrop.delivery.dto.CourierJobStop;
import com.swiftdrop.delivery.exception.DeliveryNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierJobServiceTest {

    @Mock
    private DeliverySessionRepository repository;

    @Mock
    private OrderJobClient orderJobClient;

    private final UUID deliveryId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID courierId = UUID.randomUUID();

    @Test
    @DisplayName("Same district, before pickup: the sender, the fee and the recipient")
    void sameDistrictBeforePickup() {
        session(DeliveryState.ASSIGNED, SriLankanDistrict.COLOMBO);
        order(false);

        CourierJobResponse job = service().getJob(deliveryId, courier(courierId));

        assertThat(job.pickup().name()).isEqualTo("Sender");
        assertThat(job.dropOff().name()).isEqualTo("Recipient");
        assertThat(job.feeToCollect()).isEqualByComparingTo("670.00");
        assertThat(job.notes()).isEqualTo("Ring twice");
    }

    @Test
    @DisplayName("Same district, after pickup: only the recipient, and the fee is no longer due")
    void sameDistrictAfterPickup() {
        session(DeliveryState.IN_TRANSIT, SriLankanDistrict.COLOMBO);
        order(false);

        CourierJobResponse job = service().getJob(deliveryId, courier(courierId));

        assertThat(job.pickup()).isNull();
        assertThat(job.feeToCollect()).isNull();
        assertThat(job.dropOff()).isNotNull();
    }

    @Test
    @DisplayName("Cross-district, first courier: the sender and the fee, never the recipient")
    void firstCourierNeverSeesTheRecipient() {
        session(DeliveryState.ASSIGNED, SriLankanDistrict.KANDY);
        order(false);

        CourierJobResponse job = service().getJob(deliveryId, courier(courierId));

        assertThat(job.pickup()).isNotNull();
        assertThat(job.feeToCollect()).isEqualByComparingTo("670.00");
        assertThat(job.dropOff()).isNull();
    }

    @Test
    @DisplayName("Cross-district, on the way to the hub: no contact is needed")
    void firstCourierAfterPickup() {
        session(DeliveryState.PICKED_UP, SriLankanDistrict.KANDY);
        order(false);

        CourierJobResponse job = service().getJob(deliveryId, courier(courierId));

        assertThat(job.pickup()).isNull();
        assertThat(job.dropOff()).isNull();
        assertThat(job.feeToCollect()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryState.class, names = {"AT_DESTINATION_HUB", "OUT_FOR_DELIVERY"})
    @DisplayName("Cross-district, last courier: the recipient, never the sender or the fee")
    void lastCourierNeverSeesTheSender(DeliveryState state) {
        session(state, SriLankanDistrict.KANDY);
        order(false);

        CourierJobResponse job = service().getJob(deliveryId, courier(courierId));

        assertThat(job.pickup()).isNull();
        assertThat(job.feeToCollect()).isNull();
        assertThat(job.dropOff()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryState.class, names = {"ATTEMPT_FAILED", "HELD_AT_HUB", "RETURN_IN_TRANSIT"})
    @DisplayName("After a failed attempt: the parcel goes to the hub, nobody is contacted")
    void failedAttemptGivesNoContacts(DeliveryState state) {
        session(state, SriLankanDistrict.COLOMBO);
        order(false);

        assertNothingToDo(service().getJob(deliveryId, courier(courierId)));
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryState.class, names = {"RETURN_AT_HUB", "OUT_FOR_RETURN"})
    @DisplayName("The returning courier sees the sender, and no fee")
    void returningCourierSeesTheSender(DeliveryState state) {
        session(state, SriLankanDistrict.KANDY);
        order(false);

        CourierJobResponse job = service().getJob(deliveryId, courier(courierId));

        assertThat(job.returnTo().name()).isEqualTo("Sender");
        assertThat(job.dropOff()).isNull();
        assertThat(job.pickup()).isNull();
        assertThat(job.feeToCollect()).isNull();
        assertThat(job.notes()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryState.class, names = {"DELIVERED", "RETURNED", "FAILED", "CANCELLED"})
    @DisplayName("A finished delivery gives no contacts and no amounts")
    void finishedDeliveryGivesNoContacts(DeliveryState state) {
        session(state, SriLankanDistrict.COLOMBO);
        order(false);

        assertNothingToDo(service().getJob(deliveryId, courier(courierId)));
    }

    @Test
    @DisplayName("An order cancelled after assignment: nothing to pick up or collect")
    void cancelledOrderGivesNothingToCollect() {
        session(DeliveryState.ASSIGNED, SriLankanDistrict.COLOMBO);
        order(true);

        CourierJobResponse job = service().getJob(deliveryId, courier(courierId));

        assertThat(job.cancelled()).isTrue();
        assertNothingToDo(job);
    }

    @Test
    @DisplayName("Another courier is refused before the order is read")
    void anotherCourierIsRefused() {
        session(DeliveryState.ASSIGNED, SriLankanDistrict.COLOMBO);

        assertThatThrownBy(() -> service().getJob(deliveryId, courier(UUID.randomUUID())))
                .isInstanceOf(ForbiddenException.class);
        verify(orderJobClient, never()).get(any());
    }

    @Test
    @DisplayName("A customer and a dispatcher of neither district are refused")
    void outsidersAreRefused() {
        session(DeliveryState.ASSIGNED, SriLankanDistrict.COLOMBO);

        assertThatThrownBy(() -> service().getJob(deliveryId,
                new CallerContext(UUID.randomUUID(), UserRole.CUSTOMER, null, null)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service().getJob(deliveryId,
                new CallerContext(UUID.randomUUID(), UserRole.DISPATCHER, null, SriLankanDistrict.JAFFNA)))
                .isInstanceOf(ForbiddenException.class);
        verify(orderJobClient, never()).get(any());
    }

    @Test
    @DisplayName("An unknown delivery is not found")
    void unknownDelivery() {
        when(repository.findByDeliveryId(deliveryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getJob(deliveryId, courier(courierId)))
                .isInstanceOf(DeliveryNotFoundException.class);
    }

    private static void assertNothingToDo(CourierJobResponse job) {
        assertThat(job.pickup()).isNull();
        assertThat(job.dropOff()).isNull();
        assertThat(job.returnTo()).isNull();
        assertThat(job.notes()).isNull();
        assertThat(job.feeToCollect()).isNull();
    }

    private CourierJobService service() {
        return new CourierJobService(repository, orderJobClient);
    }

    private void session(DeliveryState state, SriLankanDistrict dropOffDistrict) {
        DeliverySession session = new DeliverySession();
        session.setDeliveryId(deliveryId);
        session.setOrderId(orderId);
        session.setCourierId(courierId);
        session.setPickupDistrict(SriLankanDistrict.COLOMBO);
        session.setDeliveryDistrict(dropOffDistrict);
        session.setCurrentState(state);
        when(repository.findByDeliveryId(deliveryId)).thenReturn(Optional.of(session));
    }

    private void order(boolean cancelled) {
        when(orderJobClient.get(orderId)).thenReturn(new OrderJobClient.OrderJob(
                orderId, cancelled, OrderSpeed.STANDARD, stop("Sender"), stop("Recipient"), "Ring twice", "Books",
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TWO,
                new BigDecimal("670.00"), "LKR"));
    }

    private static CourierJobStop stop(String name) {
        return new CourierJobStop(name, "+94771234567", null, "12 Galle Road", null, "Colombo 03", "00300",
                SriLankanDistrict.COLOMBO);
    }

    private static CallerContext courier(UUID id) {
        return new CallerContext(id, UserRole.COURIER, null, SriLankanDistrict.COLOMBO);
    }
}
