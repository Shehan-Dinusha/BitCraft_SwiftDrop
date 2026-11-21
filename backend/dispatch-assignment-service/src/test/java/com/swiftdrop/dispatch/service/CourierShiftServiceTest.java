package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.CourierProfile;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.enums.VehicleType;
import com.swiftdrop.dispatch.domain.repository.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.dto.CourierShiftResponse;
import com.swiftdrop.dispatch.dto.CourierWorkloadResponse;
import com.swiftdrop.dispatch.dto.StartShiftRequest;
import com.swiftdrop.dispatch.exception.CourierNotAvailableException;
import com.swiftdrop.dispatch.exception.ShiftEndNotAllowedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourierShiftServiceTest {

    @Mock
    private CourierShiftRepository courierShiftRepository;

    @Mock
    private CourierProfileRepository courierProfileRepository;

    @InjectMocks
    private CourierShiftService courierShiftService;

    private UUID courierId;

    @BeforeEach
    void setUp() {
        courierId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Start shift: sets AVAILABLE status and takes vehicle and capacity from the courier profile")
    void testStartShift() {
        StartShiftRequest request = new StartShiftRequest(
                courierId,
                "Nuwan Perera",
                "+94771112233",
                SriLankanDistrict.COLOMBO
        );
        CourierProfile profile = new CourierProfile();
        profile.setCourierId(courierId);
        profile.setVehicleType(VehicleType.THREE_WHEELER);
        profile.setMaxCapacity(6);

        when(courierProfileRepository.findByCourierId(courierId)).thenReturn(Optional.of(profile));
        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.empty());
        when(courierShiftRepository.save(any(CourierShift.class))).thenAnswer(inv -> inv.getArgument(0));

        CourierShiftResponse response = courierShiftService.startShift(request);

        assertThat(response.courierId()).isEqualTo(courierId);
        assertThat(response.courierName()).isEqualTo("Nuwan Perera");
        assertThat(response.shiftStatus()).isEqualTo(ShiftStatus.AVAILABLE);
        assertThat(response.currentDistrict()).isEqualTo(SriLankanDistrict.COLOMBO);
        assertThat(response.maxCapacity()).isEqualTo(6);
        assertThat(response.vehicleType()).isEqualTo(VehicleType.THREE_WHEELER);
        assertThat(response.shiftStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("Start shift: refused until the courier has registered a vehicle")
    void testStartShift_RequiresVehicleProfile() {
        StartShiftRequest request = new StartShiftRequest(courierId, "Nuwan Perera", null, SriLankanDistrict.COLOMBO);
        when(courierProfileRepository.findByCourierId(courierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courierShiftService.startShift(request))
                .isInstanceOf(CourierNotAvailableException.class)
                .hasMessageContaining("vehicle");
        verify(courierShiftRepository, never()).save(any());
    }

    @Test
    @DisplayName("End shift: successfully ends shift when 0 active parcels")
    void testEndShift_Success() {
        CourierShift shift = new CourierShift();
        shift.setCourierId(courierId);
        shift.setCourierName("Nuwan Perera");
        shift.setCurrentDistrict(SriLankanDistrict.COLOMBO);
        shift.setShiftStatus(ShiftStatus.AVAILABLE);
        shift.setActiveParcelsCount(0);

        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(shift));
        when(courierShiftRepository.save(any(CourierShift.class))).thenAnswer(inv -> inv.getArgument(0));

        CourierShiftResponse response = courierShiftService.endShift(courierId);

        assertThat(response.shiftStatus()).isEqualTo(ShiftStatus.OFFLINE);
        assertThat(response.shiftEndedAt()).isNotNull();
    }

    @Test
    @DisplayName("End shift: rejected with ShiftEndNotAllowedException while active parcels remain, nothing changes")
    void testEndShift_ParcelsRemaining() {
        CourierShift shift = new CourierShift();
        shift.setCourierId(courierId);
        shift.setActiveParcelsCount(2);
        shift.setShiftStatus(ShiftStatus.BUSY);

        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> courierShiftService.endShift(courierId))
                .isInstanceOf(ShiftEndNotAllowedException.class)
                .hasMessageContaining("2 active parcel(s)");

        assertThat(shift.getShiftStatus()).isEqualTo(ShiftStatus.BUSY);
        assertThat(shift.getActiveParcelsCount()).isEqualTo(2);
        assertThat(shift.getShiftEndedAt()).isNull();
        verify(courierShiftRepository, never()).save(any());
    }

    @Test
    @DisplayName("Workloads: returns active parcel counts for all courier shifts")
    void testGetWorkloads() {
        CourierShift shift = new CourierShift();
        shift.setCourierId(courierId);
        shift.setCourierName("Nuwan Perera");
        shift.setCurrentDistrict(SriLankanDistrict.COLOMBO);
        shift.setShiftStatus(ShiftStatus.AVAILABLE);
        shift.setActiveParcelsCount(2);
        shift.setMaxCapacity(6);
        when(courierShiftRepository.findAll()).thenReturn(List.of(shift));

        List<CourierWorkloadResponse> workloads = courierShiftService.getWorkloads();

        assertThat(workloads).singleElement().satisfies(workload -> {
            assertThat(workload.courierId()).isEqualTo(courierId);
            assertThat(workload.activeParcels()).isEqualTo(2);
            assertThat(workload.maxCapacity()).isEqualTo(6);
        });
    }
}
