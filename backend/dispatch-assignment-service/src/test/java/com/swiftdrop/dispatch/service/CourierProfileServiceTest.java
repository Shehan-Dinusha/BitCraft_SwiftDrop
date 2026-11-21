package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.dispatch.domain.entity.CourierProfile;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.VehicleType;
import com.swiftdrop.dispatch.domain.repository.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.dto.CourierProfileResponse;
import com.swiftdrop.dispatch.dto.CourierVehicleRequest;
import com.swiftdrop.dispatch.dto.OrderCourierResponse;
import com.swiftdrop.dispatch.exception.CourierProfileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierProfileServiceTest {

    @Mock private CourierProfileRepository profileRepository;
    @Mock private CourierShiftRepository shiftRepository;
    @Mock private DispatchAssignmentRepository assignmentRepository;

    @InjectMocks
    private CourierProfileService service;

    private UUID courierId;
    private CallerContext courier;
    private CallerContext colomboDispatcher;

    @BeforeEach
    void setUp() {
        courierId = UUID.randomUUID();
        courier = new CallerContext(courierId, UserRole.COURIER, null, SriLankanDistrict.COLOMBO);
        colomboDispatcher = new CallerContext(UUID.randomUUID(), UserRole.DISPATCHER, null, SriLankanDistrict.COLOMBO);
        lenient().when(profileRepository.save(any(CourierProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void aCourierRegistersTheirVehicleOnceWithTheTypesDefaultCapacityAndANormalisedPlate() {
        when(profileRepository.findByCourierId(courierId)).thenReturn(Optional.empty());

        CourierProfileResponse profile = service.registerOwn(courier,
                vehicle(VehicleType.THREE_WHEELER, " wp  bab 1234 ", 99));

        assertThat(profile.plateNumber()).isEqualTo("WP BAB-1234");
        assertThat(profile.maxCapacity()).isEqualTo(10);
        assertThat(profile.district()).isEqualTo(SriLankanDistrict.COLOMBO);
        assertThat(profile.licenceNumber()).isEqualTo("B1234567");
    }

    @Test
    void aCourierCannotRegisterASecondTime() {
        when(profileRepository.findByCourierId(courierId)).thenReturn(Optional.of(existing(VehicleType.MOTORCYCLE, 5)));

        assertThatThrownBy(() -> service.registerOwn(courier, vehicle(VehicleType.VAN, "CAB-1234", null)))
                .isInstanceOf(CourierProfileException.class)
                .satisfies(e -> assertThat(((CourierProfileException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void aPlateRegisteredToAnotherCourierIsRefused() {
        CourierProfile other = existing(VehicleType.MOTORCYCLE, 5);
        other.setCourierId(UUID.randomUUID());
        when(profileRepository.findByCourierId(courierId)).thenReturn(Optional.empty());
        when(profileRepository.findByPlateNumber("WP BAB-1234")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.registerOwn(courier, vehicle(VehicleType.MOTORCYCLE, "WP BAB-1234", null)))
                .isInstanceOf(CourierProfileException.class)
                .hasMessageContaining("already registered");
        verify(profileRepository, never()).save(any());
    }

    @Test
    void somethingThatIsNotAPlateIsRejected() {
        assertThatThrownBy(() -> CourierProfileService.normalisePlate("hello"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(CourierProfileService.normalisePlate("65-1234")).isEqualTo("65-1234");
        assertThat(CourierProfileService.normalisePlate("cp kx - 9876")).isEqualTo("CP KX-9876");
    }

    @Test
    void changingTheVehicleTypeResetsCapacityAndUpdatesTheShift() {
        CourierProfile profile = existing(VehicleType.MOTORCYCLE, 5);
        CourierShift shift = new CourierShift();
        shift.setCourierId(courierId);
        when(profileRepository.findByCourierId(courierId)).thenReturn(Optional.of(profile));
        when(shiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(shift));

        CourierProfileResponse updated = service.updateByOperations(colomboDispatcher, courierId,
                vehicle(VehicleType.LORRY, "WP LA-4321", null));

        assertThat(updated.maxCapacity()).isEqualTo(50);
        assertThat(shift.getVehicleType()).isEqualTo(VehicleType.LORRY);
        assertThat(shift.getMaxCapacity()).isEqualTo(50);
    }

    @Test
    void aDispatcherCanSetAnExplicitCapacity() {
        when(profileRepository.findByCourierId(courierId)).thenReturn(Optional.of(existing(VehicleType.VAN, 25)));

        CourierProfileResponse updated = service.updateByOperations(colomboDispatcher, courierId,
                vehicle(VehicleType.VAN, "WP LA-4321", 18));

        assertThat(updated.maxCapacity()).isEqualTo(18);
    }

    @Test
    void aDispatcherCannotChangeAnotherDistrictsCourier() {
        CourierProfile galle = existing(VehicleType.MOTORCYCLE, 5);
        galle.setDistrict(SriLankanDistrict.GALLE);
        when(profileRepository.findByCourierId(courierId)).thenReturn(Optional.of(galle));

        assertThatThrownBy(() -> service.updateByOperations(colomboDispatcher, courierId,
                vehicle(VehicleType.VAN, "WP LA-4321", null)))
                .isInstanceOf(ForbiddenException.class);
        verify(profileRepository, never()).save(any());
    }

    @Test
    void aCourierCannotUseTheDispatcherUpdate() {
        assertThatThrownBy(() -> service.updateByOperations(courier, courierId,
                vehicle(VehicleType.VAN, "WP LA-4321", 200)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void theOrdersCustomerSeesTheCouriersFirstNameAndVehicleOnly() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment(customerId, AssignmentStatus.ASSIGNED)));
        when(profileRepository.findByCourierId(courierId)).thenReturn(Optional.of(existing(VehicleType.MOTORCYCLE, 5)));

        OrderCourierResponse seen = service.courierForOrder(
                new CallerContext(customerId, UserRole.CUSTOMER, null, null), orderId);

        assertThat(seen.firstName()).isEqualTo("Kasun");
        assertThat(seen.plateNumber()).isEqualTo("WP BAB-1234");
        assertThat(seen.vehicleType()).isEqualTo(VehicleType.MOTORCYCLE);
    }

    @Test
    void anotherCustomerCannotSeeTheCourier() {
        UUID orderId = UUID.randomUUID();
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment(UUID.randomUUID(), AssignmentStatus.ASSIGNED)));

        assertThatThrownBy(() -> service.courierForOrder(
                new CallerContext(UUID.randomUUID(), UserRole.CUSTOMER, null, null), orderId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void nothingIsShownOnceTheDeliveryIsComplete() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment(customerId, AssignmentStatus.COMPLETED)));

        assertThat(service.courierForOrder(new CallerContext(customerId, UserRole.CUSTOMER, null, null), orderId)).isNull();
    }

    private CourierProfile existing(VehicleType type, int capacity) {
        CourierProfile profile = new CourierProfile();
        profile.setCourierId(courierId);
        profile.setDistrict(SriLankanDistrict.COLOMBO);
        profile.setVehicleType(type);
        profile.setPlateNumber("WP BAB-1234");
        profile.setVehicleModel("Honda Dio");
        profile.setVehicleColour("Black");
        profile.setLicenceNumber("B1234567");
        profile.setMaxCapacity(capacity);
        return profile;
    }

    private DispatchAssignment assignment(UUID customerId, AssignmentStatus status) {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setCustomerId(customerId);
        assignment.setCourierId(courierId);
        assignment.setCourierName("Kasun Perera");
        assignment.setStatus(status);
        return assignment;
    }

    private static CourierVehicleRequest vehicle(VehicleType type, String plate, Integer capacity) {
        return new CourierVehicleRequest(type, plate, "Honda Dio", "Black", "b 1234567", capacity);
    }
}
