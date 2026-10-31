package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.enums.VehicleType;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.dto.CourierLocationPingRequest;
import com.swiftdrop.dispatch.dto.CourierShiftResponse;
import com.swiftdrop.dispatch.dto.StartShiftRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourierShiftServiceTest {

    @Mock
    private CourierShiftRepository courierShiftRepository;

    @InjectMocks
    private CourierShiftService courierShiftService;

    private UUID courierId;

    @BeforeEach
    void setUp() {
        courierId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Start shift: sets AVAILABLE status and registers starting district")
    void testStartShift() {
        StartShiftRequest request = new StartShiftRequest(
                courierId,
                "Nuwan Perera",
                "+94771112233",
                VehicleType.MOTORCYCLE,
                SriLankanDistrict.COLOMBO,
                6
        );

        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.empty());
        when(courierShiftRepository.save(any(CourierShift.class))).thenAnswer(inv -> inv.getArgument(0));

        CourierShiftResponse response = courierShiftService.startShift(request);

        assertThat(response.courierId()).isEqualTo(courierId);
        assertThat(response.courierName()).isEqualTo("Nuwan Perera");
        assertThat(response.shiftStatus()).isEqualTo(ShiftStatus.AVAILABLE);
        assertThat(response.currentDistrict()).isEqualTo(SriLankanDistrict.COLOMBO);
        assertThat(response.maxCapacity()).isEqualTo(6);
        assertThat(response.shiftStartedAt()).isNotNull();
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
    @DisplayName("End shift: throws IllegalStateException when active parcels remain")
    void testEndShift_ParcelsRemaining() {
        CourierShift shift = new CourierShift();
        shift.setCourierId(courierId);
        shift.setActiveParcelsCount(2);

        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> courierShiftService.endShift(courierId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active parcels assigned");
    }

    @Test
    @DisplayName("Heartbeat location ping: updates district and coordinates")
    void testLocationPing() {
        CourierShift shift = new CourierShift();
        shift.setCourierId(courierId);
        shift.setCurrentDistrict(SriLankanDistrict.COLOMBO);

        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(shift));
        when(courierShiftRepository.save(any(CourierShift.class))).thenAnswer(inv -> inv.getArgument(0));

        CourierLocationPingRequest ping = new CourierLocationPingRequest(
                courierId,
                SriLankanDistrict.GAMPAHA,
                BigDecimal.valueOf(7.0840),
                BigDecimal.valueOf(79.9939)
        );

        CourierShiftResponse response = courierShiftService.updateLocation(ping);

        assertThat(response.currentDistrict()).isEqualTo(SriLankanDistrict.GAMPAHA);
        assertThat(response.latitude()).isEqualTo(BigDecimal.valueOf(7.0840));
        assertThat(response.longitude()).isEqualTo(BigDecimal.valueOf(79.9939));
        assertThat(response.lastHeartbeatAt()).isNotNull();
    }
}
