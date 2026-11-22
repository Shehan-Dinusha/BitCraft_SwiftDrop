package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentLeg;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.dto.ManualAssignRequest;
import com.swiftdrop.dispatch.exception.AssignmentConflictException;
import com.swiftdrop.dispatch.exception.CourierNotAvailableException;
import com.swiftdrop.dispatch.messaging.publisher.DispatchEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A Colombo -> Kandy order through both hubs, as dispatch sees it. */
@ExtendWith(MockitoExtension.class)
class HubLegTest {

    @Mock private DispatchAssignmentRepository assignments;
    @Mock private CourierShiftRepository couriers;
    @Mock private DispatchEventPublisher eventPublisher;

    private DispatchAssignmentService dispatchService;
    private ManualOverrideService overrideService;

    private final UUID orderId = UUID.randomUUID();
    private final UUID colomboCourier = UUID.randomUUID();
    private DispatchAssignment order;

    @BeforeEach
    void colomboToKandyPickedUp() {
        dispatchService = new DispatchAssignmentService(assignments, couriers);
        overrideService = new ManualOverrideService(assignments, couriers, eventPublisher);
        order = new DispatchAssignment();
        order.setOrderId(orderId);
        order.setTrackingNumber("SD-7K2P-Q9XM");
        order.setPickupDistrict(SriLankanDistrict.COLOMBO);
        order.setDeliveryDistrict(SriLankanDistrict.KANDY);
        order.setOrderSpeed(OrderSpeed.STANDARD);
        order.setStatus(AssignmentStatus.ASSIGNED);
        order.setCourierId(colomboCourier);
        order.setPickedUpAt(Instant.now());
        lenient().when(assignments.findByOrderId(orderId)).thenReturn(Optional.of(order));
        lenient().when(assignments.save(any(DispatchAssignment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ParcelStatusChangedEvent hubEvent(InternalQueueStatus stage) {
        return new ParcelStatusChangedEvent(UUID.randomUUID(), UUID.randomUUID(), "SD-7K2P-Q9XM", orderId, null,
                PublicOrderStatus.IN_TRANSIT, PublicOrderStatus.IN_TRANSIT, stage,
                SriLankanDistrict.COLOMBO, SriLankanDistrict.KANDY, null, Instant.now());
    }

    private static CourierShift courierIn(UUID id, SriLankanDistrict district) {
        CourierShift courier = new CourierShift();
        courier.setCourierId(id);
        courier.setCourierName("Courier " + district);
        courier.setCurrentDistrict(district);
        courier.setShiftStatus(ShiftStatus.AVAILABLE);
        courier.setActiveParcelsCount(1);
        courier.setMaxCapacity(5);
        return courier;
    }

    @Test
    @DisplayName("Handed in at the Colombo hub: the courier is freed and the order waits to be sent")
    void handedInAtTheOriginHub() {
        CourierShift courier = courierIn(colomboCourier, SriLankanDistrict.COLOMBO);
        when(couriers.findByCourierId(colomboCourier)).thenReturn(Optional.of(courier));

        dispatchService.handleParcelStatusChanged(hubEvent(InternalQueueStatus.AT_HUB));

        assertThat(order.getStatus()).isEqualTo(AssignmentStatus.IN_TRANSFER);
        assertThat(order.getHubStage()).isEqualTo(InternalQueueStatus.AT_HUB);
        assertThat(order.getCourierId()).isNull();
        assertThat(order.getFirstMileCourierId()).isEqualTo(colomboCourier);
        assertThat(courier.getActiveParcelsCount()).isZero();

        // A repeated event changes nothing and releases no more workload.
        dispatchService.handleParcelStatusChanged(hubEvent(InternalQueueStatus.AT_HUB));
        assertThat(courier.getActiveParcelsCount()).isZero();
    }

    @Test
    @DisplayName("Outbound, then accepted at Kandy: it becomes Kandy's unassigned last-mile order")
    void outboundThenAcceptedAtTheDestinationHub() {
        order.setStatus(AssignmentStatus.IN_TRANSFER);
        order.setCourierId(null);
        order.setHubStage(InternalQueueStatus.AT_HUB);

        dispatchService.handleParcelStatusChanged(hubEvent(InternalQueueStatus.OUTBOUND));
        assertThat(order.getHubStage()).isEqualTo(InternalQueueStatus.OUTBOUND);
        assertThat(order.getResponsibleDistrict()).isEqualTo(SriLankanDistrict.COLOMBO);

        dispatchService.handleParcelStatusChanged(hubEvent(InternalQueueStatus.INCOMING));
        assertThat(order.getStatus()).isEqualTo(AssignmentStatus.UNASSIGNED);
        assertThat(order.getLeg()).isEqualTo(AssignmentLeg.LAST_MILE);
        assertThat(order.getResponsibleDistrict()).isEqualTo(SriLankanDistrict.KANDY);
        assertThat(order.getPickedUpAt()).isNull();
    }

    @Test
    @DisplayName("Only Kandy assigns the last mile, with a Kandy courier, and no status goes backwards")
    void kandyAssignsTheLastMile() {
        order.setStatus(AssignmentStatus.UNASSIGNED);
        order.setCourierId(null);
        order.setLeg(AssignmentLeg.LAST_MILE);
        order.setPickedUpAt(null);
        UUID kandyCourier = UUID.randomUUID();
        ManualAssignRequest request = new ManualAssignRequest(orderId, kandyCourier, "DISPATCHER", null);

        CallerContext colombo = new CallerContext(UUID.randomUUID(), UserRole.DISPATCHER, null, SriLankanDistrict.COLOMBO);
        assertThatThrownBy(() -> overrideService.manualAssign(request, null, colombo))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("Kandy");

        when(couriers.findByCourierId(kandyCourier)).thenReturn(Optional.of(courierIn(kandyCourier, SriLankanDistrict.COLOMBO)));
        CallerContext kandy = new CallerContext(UUID.randomUUID(), UserRole.DISPATCHER, null, SriLankanDistrict.KANDY);
        assertThatThrownBy(() -> overrideService.manualAssign(request, null, kandy))
                .isInstanceOf(CourierNotAvailableException.class).hasMessageContaining("Kandy hub");

        when(couriers.findByCourierId(kandyCourier)).thenReturn(Optional.of(courierIn(kandyCourier, SriLankanDistrict.KANDY)));
        overrideService.manualAssign(request, null, kandy);
        assertThat(order.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        verify(eventPublisher).publishCourierAssigned(any());
        verify(eventPublisher, never()).publishParcelStatusChanged(any());
    }

    @Test
    @DisplayName("Nobody assigns a courier while the parcel is between the hubs")
    void noCourierBetweenTheHubs() {
        order.setStatus(AssignmentStatus.IN_TRANSFER);
        order.setCourierId(null);
        CallerContext admin = new CallerContext(UUID.randomUUID(), UserRole.SYSTEM_ADMIN, null, null);
        assertThatThrownBy(() -> overrideService.manualAssign(
                new ManualAssignRequest(orderId, UUID.randomUUID(), "ADMIN", null), null, admin))
                .isInstanceOf(AssignmentConflictException.class).hasMessageContaining("between district hubs");
    }

    @Test
    @DisplayName("A same-district courier on the way still counts as the normal pickup boundary")
    void sameDistrictOutboundIsNotAHubStep() {
        order.setDeliveryDistrict(SriLankanDistrict.COLOMBO);
        order.setPickedUpAt(null);
        dispatchService.handleParcelStatusChanged(hubEvent(InternalQueueStatus.OUTBOUND));
        assertThat(order.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(order.getPickedUpAt()).isNotNull();
        assertThat(order.getHubStage()).isNull();
    }
}
