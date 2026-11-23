package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.dispatch.client.CourierCashClient;
import com.swiftdrop.dispatch.domain.entity.CourierProfile;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.enums.VehicleType;
import com.swiftdrop.dispatch.domain.entity.ClosedDay;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentLeg;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.repository.ClosedDayRepository;
import com.swiftdrop.dispatch.domain.repository.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.dto.CourierShiftResponse;
import com.swiftdrop.dispatch.dto.CourierWorkloadResponse;
import com.swiftdrop.dispatch.dto.StartShiftRequest;
import com.swiftdrop.dispatch.exception.CourierNotAvailableException;
import com.swiftdrop.dispatch.exception.ShiftEndNotAllowedException;
import com.swiftdrop.dispatch.exception.ShiftRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private CourierCashClient courierCashClient;

    @Mock
    private CourierShiftRepository courierShiftRepository;

    @Mock
    private CourierProfileRepository courierProfileRepository;

    @Mock
    private com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository assignmentRepository;

    @Mock
    private ClosedDayRepository closedDayRepository;

    private CourierShiftService courierShiftService;

    private UUID courierId;

    @BeforeEach
    void setUp() {
        courierId = UUID.randomUUID();
        at("2026-11-24T10:00"); // a Tuesday morning
    }

    /** The service as it runs at {@code localTime}, Sri Lanka time. */
    private void at(String localTime) {
        Clock clock = Clock.fixed(LocalDateTime.parse(localTime).atZone(WorkingDay.ZONE).toInstant(), WorkingDay.ZONE);
        courierShiftService = new CourierShiftService(courierShiftRepository, courierProfileRepository,
                assignmentRepository, courierCashClient, new WorkingDay(clock, closedDayRepository));
    }

    private CourierShift onShift(int parcels) {
        CourierShift shift = new CourierShift();
        shift.setCourierId(courierId);
        shift.setCourierName("Nuwan Perera");
        shift.setCurrentDistrict(SriLankanDistrict.COLOMBO);
        shift.setVehicleType(VehicleType.MOTORCYCLE);
        shift.setMaxCapacity(5);
        shift.setActiveParcelsCount(parcels);
        shift.setShiftStatus(parcels >= 5 ? ShiftStatus.BUSY : ShiftStatus.AVAILABLE);
        shift.setShiftStartedAt(LocalDateTime.parse("2026-11-24T08:05").atZone(WorkingDay.ZONE).toInstant());
        return shift;
    }

    @Test
    @DisplayName("Start shift: refused on Sundays, listed holidays, before 7:30 and after 17:00, saying when to come")
    void startShiftOnlyOnWorkingHours() {
        when(courierProfileRepository.findByCourierId(courierId)).thenReturn(Optional.of(profileIn(SriLankanDistrict.COLOMBO)));
        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.empty());
        LocalDate poya = LocalDate.of(2026, 12, 4);
        when(closedDayRepository.findById(any())).thenReturn(Optional.empty());
        when(closedDayRepository.findById(poya)).thenReturn(Optional.of(new ClosedDay(poya, "Unduvap Poya Day")));
        StartShiftRequest request = new StartShiftRequest(courierId, "Nuwan Perera", null);

        at("2026-11-29T10:00");
        assertThatThrownBy(() -> courierShiftService.startShift(request, courier(SriLankanDistrict.COLOMBO)))
                .isInstanceOf(ShiftRuleException.class).hasMessageContaining("Sunday").hasMessageContaining("Monday 30 November");
        at("2026-12-04T10:00");
        assertThatThrownBy(() -> courierShiftService.startShift(request, courier(SriLankanDistrict.COLOMBO)))
                .isInstanceOf(ShiftRuleException.class).hasMessageContaining("Unduvap Poya Day").hasMessageContaining("Saturday 5 December");
        at("2026-11-24T07:15");
        assertThatThrownBy(() -> courierShiftService.startShift(request, courier(SriLankanDistrict.COLOMBO)))
                .isInstanceOf(ShiftRuleException.class).hasMessageContaining("7:30");
        at("2026-11-24T17:00");
        assertThatThrownBy(() -> courierShiftService.startShift(request, courier(SriLankanDistrict.COLOMBO)))
                .isInstanceOf(ShiftRuleException.class).hasMessageContaining("17:00").hasMessageContaining("Wednesday 25 November");
        verify(courierShiftRepository, never()).save(any());
    }

    @Test
    @DisplayName("Start shift: from 7:30; after 9:00 it shows as a late start, and the day's break and flag start fresh")
    void startShiftLate() {
        when(courierProfileRepository.findByCourierId(courierId)).thenReturn(Optional.of(profileIn(SriLankanDistrict.COLOMBO)));
        CourierShift yesterday = onShift(0);
        yesterday.setShiftStatus(ShiftStatus.OFFLINE);
        yesterday.setBreakStartedAt(Instant.parse("2026-11-23T07:00:00Z"));
        yesterday.setClosedAutomatically(true);
        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(yesterday));
        when(courierShiftRepository.save(any(CourierShift.class))).thenAnswer(inv -> inv.getArgument(0));
        StartShiftRequest request = new StartShiftRequest(courierId, "Nuwan Perera", null);

        at("2026-11-24T07:30");
        CourierShiftResponse early = courierShiftService.startShift(request, courier(SriLankanDistrict.COLOMBO));
        assertThat(early.startedLate()).isFalse();
        assertThat(early.breakStartedAt()).isNull();
        assertThat(early.closedAutomatically()).isFalse();

        yesterday.setShiftStatus(ShiftStatus.OFFLINE);
        at("2026-11-24T09:20");
        assertThat(courierShiftService.startShift(request, courier(SriLankanDistrict.COLOMBO)).startedLate()).isTrue();
    }

    @Test
    @DisplayName("Start shift: a shift already running is returned unchanged, whatever the time")
    void startShiftAlreadyRunning() {
        when(courierProfileRepository.findByCourierId(courierId)).thenReturn(Optional.of(profileIn(SriLankanDistrict.COLOMBO)));
        CourierShift running = onShift(2);
        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(running));

        at("2026-11-24T18:00");
        CourierShiftResponse response = courierShiftService.startShift(
                new StartShiftRequest(courierId, "Someone else", null), courier(SriLankanDistrict.COLOMBO));

        assertThat(response.courierName()).isEqualTo("Nuwan Perera");
        assertThat(response.shiftStartedAt()).isEqualTo(running.getShiftStartedAt());
        verify(courierShiftRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lunch break: one per shift, started between 12:00 and 14:30, 45 minutes; afterwards the parcel count decides")
    void oneLunchBreak() {
        CourierShift shift = onShift(4);
        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(shift));
        when(courierShiftRepository.save(any(CourierShift.class))).thenAnswer(inv -> inv.getArgument(0));

        at("2026-11-24T11:50");
        assertThatThrownBy(() -> courierShiftService.updateStatus(courierId, ShiftStatus.ON_BREAK))
                .isInstanceOf(ShiftRuleException.class).hasMessageContaining("between 12:00 and 14:30");

        at("2026-11-24T12:30");
        CourierShiftResponse onBreak = courierShiftService.updateStatus(courierId, ShiftStatus.ON_BREAK);
        assertThat(onBreak.shiftStatus()).isEqualTo(ShiftStatus.ON_BREAK);
        assertThat(onBreak.breakEndsAt()).isEqualTo(onBreak.breakStartedAt().plus(Duration.ofMinutes(45)));

        // Given a parcel while on break: they stay on it, and come back full.
        shift.setActiveParcelsCount(5);
        at("2026-11-24T13:10");
        CourierShiftResponse back = courierShiftService.updateStatus(courierId, ShiftStatus.AVAILABLE);
        assertThat(back.shiftStatus()).isEqualTo(ShiftStatus.BUSY);
        assertThat(back.breakEndedAt()).isNotNull();

        at("2026-11-24T14:00");
        assertThatThrownBy(() -> courierShiftService.updateStatus(courierId, ShiftStatus.ON_BREAK))
                .isInstanceOf(ShiftRuleException.class).hasMessageContaining("had your break");
    }

    @Test
    @DisplayName("Status: a shift cannot be ended or set busy here, and nothing changes off shift")
    void statusCannotEndTheShift() {
        CourierShift shift = onShift(3);
        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> courierShiftService.updateStatus(courierId, ShiftStatus.OFFLINE))
                .isInstanceOf(ShiftRuleException.class).hasMessageContaining("End the shift");
        assertThatThrownBy(() -> courierShiftService.updateStatus(courierId, ShiftStatus.BUSY))
                .isInstanceOf(ShiftRuleException.class);
        shift.setShiftStatus(ShiftStatus.OFFLINE);
        assertThatThrownBy(() -> courierShiftService.updateStatus(courierId, ShiftStatus.AVAILABLE))
                .isInstanceOf(ShiftRuleException.class).hasMessageContaining("Start the shift");
        verify(courierShiftRepository, never()).save(any());
    }

    @Test
    @DisplayName("Start run: only once the hub parcels are loaded, not on break; afterwards it changes nothing")
    void startRun() {
        CourierShift shift = onShift(2);
        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(shift));
        when(courierShiftRepository.save(any(CourierShift.class))).thenAnswer(inv -> inv.getArgument(0));
        DispatchAssignment waiting = held("SD-HUB-0001", AssignmentLeg.LAST_MILE, null);
        DispatchAssignment loaded = held("SD-HUB-0002", AssignmentLeg.LAST_MILE, Instant.parse("2026-11-24T03:00:00Z"));
        DispatchAssignment pickup = held("SD-PICK-0003", AssignmentLeg.FIRST_MILE, null);
        when(assignmentRepository.findByCourierId(courierId)).thenReturn(List.of(waiting, loaded, pickup));

        assertThatThrownBy(() -> courierShiftService.startRun(courierId))
                .isInstanceOf(ShiftRuleException.class).hasMessageContaining("SD-HUB-0001")
                .hasMessageNotContaining("SD-HUB-0002").hasMessageNotContaining("SD-PICK-0003");

        waiting.setPickedUpAt(Instant.parse("2026-11-24T03:10:00Z"));
        shift.setShiftStatus(ShiftStatus.ON_BREAK);
        assertThatThrownBy(() -> courierShiftService.startRun(courierId))
                .isInstanceOf(ShiftRuleException.class).hasMessageContaining("break");

        shift.setShiftStatus(ShiftStatus.AVAILABLE);
        CourierShiftResponse left = courierShiftService.startRun(courierId);
        assertThat(left.runStartedAt()).isEqualTo(LocalDateTime.parse("2026-11-24T10:00").atZone(WorkingDay.ZONE).toInstant());
        assertThat(courierShiftService.startRun(courierId).runStartedAt()).isEqualTo(left.runStartedAt());
        verify(courierShiftRepository, times(1)).save(shift);
    }

    private DispatchAssignment held(String tracking, AssignmentLeg leg, Instant pickedUpAt) {
        DispatchAssignment parcel = new DispatchAssignment();
        parcel.setTrackingNumber(tracking);
        parcel.setCourierId(courierId);
        parcel.setStatus(AssignmentStatus.ASSIGNED);
        parcel.setLeg(leg);
        parcel.setPickedUpAt(pickedUpAt);
        return parcel;
    }

    @Test
    @DisplayName("At night every open shift is closed and flagged, a break included; parcels stay counted")
    void forgottenShiftsCloseAtNight() {
        CourierShift working = onShift(2);
        CourierShift onBreak = onShift(0);
        onBreak.setShiftStatus(ShiftStatus.ON_BREAK);
        onBreak.setBreakStartedAt(Instant.parse("2026-11-24T07:00:00Z"));
        when(courierShiftRepository.findByShiftStatusNot(ShiftStatus.OFFLINE)).thenReturn(List.of(working, onBreak));

        at("2026-11-24T20:00");
        assertThat(courierShiftService.closeForgottenShifts()).isEqualTo(2);

        assertThat(List.of(working, onBreak)).allSatisfy(shift -> {
            assertThat(shift.getShiftStatus()).isEqualTo(ShiftStatus.OFFLINE);
            assertThat(shift.isClosedAutomatically()).isTrue();
            assertThat(shift.getShiftEndedAt()).isNotNull();
        });
        assertThat(onBreak.getBreakEndedAt()).isNotNull();
        assertThat(working.getActiveParcelsCount()).isEqualTo(2);
        verify(courierShiftRepository).saveAll(List.of(working, onBreak));
    }

    @Test
    @DisplayName("Start shift: sets AVAILABLE status and takes vehicle and capacity from the courier profile")
    void testStartShift() {
        StartShiftRequest request = new StartShiftRequest(
                courierId,
                "Nuwan Perera",
                "+94771112233"
        );
        CourierProfile profile = new CourierProfile();
        profile.setCourierId(courierId);
        profile.setDistrict(SriLankanDistrict.COLOMBO);
        profile.setVehicleType(VehicleType.THREE_WHEELER);
        profile.setMaxCapacity(6);

        when(courierProfileRepository.findByCourierId(courierId)).thenReturn(Optional.of(profile));
        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.empty());
        when(courierShiftRepository.save(any(CourierShift.class))).thenAnswer(inv -> inv.getArgument(0));

        CourierShiftResponse response = courierShiftService.startShift(request, courier(SriLankanDistrict.COLOMBO));

        assertThat(response.courierId()).isEqualTo(courierId);
        assertThat(response.courierName()).isEqualTo("Nuwan Perera");
        assertThat(response.shiftStatus()).isEqualTo(ShiftStatus.AVAILABLE);
        assertThat(response.currentDistrict()).isEqualTo(SriLankanDistrict.COLOMBO);
        assertThat(response.maxCapacity()).isEqualTo(6);
        assertThat(response.vehicleType()).isEqualTo(VehicleType.THREE_WHEELER);
        assertThat(response.shiftStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("Start shift: a courier an admin moved works in their new district, and the profile follows")
    void testStartShift_FollowsAccountDistrict() {
        CourierProfile profile = profileIn(SriLankanDistrict.COLOMBO);
        when(courierProfileRepository.findByCourierId(courierId)).thenReturn(Optional.of(profile));
        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.empty());
        when(courierShiftRepository.save(any(CourierShift.class))).thenAnswer(inv -> inv.getArgument(0));

        CourierShiftResponse response = courierShiftService.startShift(
                new StartShiftRequest(courierId, "Nuwan Perera", null), courier(SriLankanDistrict.GAMPAHA));

        assertThat(response.currentDistrict()).isEqualTo(SriLankanDistrict.GAMPAHA);
        assertThat(profile.getDistrict()).isEqualTo(SriLankanDistrict.GAMPAHA);
    }

    @Test
    @DisplayName("Start shift: a dispatcher cannot start a shift for another district's courier")
    void testStartShift_DispatcherOfAnotherDistrict() {
        when(courierProfileRepository.findByCourierId(courierId))
                .thenReturn(Optional.of(profileIn(SriLankanDistrict.KANDY)));
        CallerContext dispatcher = new CallerContext(
                UUID.randomUUID(), UserRole.DISPATCHER, null, SriLankanDistrict.COLOMBO);

        assertThatThrownBy(() -> courierShiftService.startShift(
                new StartShiftRequest(courierId, "Nuwan Perera", null), dispatcher))
                .isInstanceOf(ForbiddenException.class);
        verify(courierShiftRepository, never()).save(any());
    }

    private CallerContext courier(SriLankanDistrict district) {
        return new CallerContext(courierId, UserRole.COURIER, null, district);
    }

    private CourierProfile profileIn(SriLankanDistrict district) {
        CourierProfile profile = new CourierProfile();
        profile.setCourierId(courierId);
        profile.setDistrict(district);
        profile.setVehicleType(VehicleType.MOTORCYCLE);
        profile.setMaxCapacity(5);
        return profile;
    }

    @Test
    @DisplayName("Start shift: refused until the courier has registered a vehicle")
    void testStartShift_RequiresVehicleProfile() {
        StartShiftRequest request = new StartShiftRequest(courierId, "Nuwan Perera", null);
        when(courierProfileRepository.findByCourierId(courierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courierShiftService.startShift(request, courier(SriLankanDistrict.COLOMBO)))
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

        when(courierCashClient.inHand(courierId)).thenReturn(BigDecimal.ZERO);

        CourierShiftResponse response = courierShiftService.endShift(courierId, courier(SriLankanDistrict.COLOMBO));

        assertThat(response.shiftStatus()).isEqualTo(ShiftStatus.OFFLINE);
        assertThat(response.shiftEndedAt()).isNotNull();
    }

    @Test
    @DisplayName("End shift: a courier who still holds cash hands it over first; their dispatcher may end it anyway")
    void testEndShift_CashInHand() {
        CourierShift shift = new CourierShift();
        shift.setCourierId(courierId);
        shift.setCurrentDistrict(SriLankanDistrict.COLOMBO);
        shift.setShiftStatus(ShiftStatus.AVAILABLE);
        shift.setActiveParcelsCount(0);
        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(shift));
        when(courierCashClient.inHand(courierId)).thenReturn(new BigDecimal("1340.00"));

        assertThatThrownBy(() -> courierShiftService.endShift(courierId, courier(SriLankanDistrict.COLOMBO)))
                .isInstanceOf(ShiftEndNotAllowedException.class)
                .hasMessageContaining("1,340.00");
        assertThat(shift.getShiftStatus()).isEqualTo(ShiftStatus.AVAILABLE);

        when(courierShiftRepository.save(any(CourierShift.class))).thenAnswer(inv -> inv.getArgument(0));
        CallerContext dispatcher = new CallerContext(
                UUID.randomUUID(), UserRole.DISPATCHER, null, SriLankanDistrict.COLOMBO);
        assertThat(courierShiftService.endShift(courierId, dispatcher).shiftStatus()).isEqualTo(ShiftStatus.OFFLINE);
    }

    @Test
    @DisplayName("End shift: rejected with ShiftEndNotAllowedException while active parcels remain, nothing changes")
    void testEndShift_ParcelsRemaining() {
        CourierShift shift = new CourierShift();
        shift.setCourierId(courierId);
        shift.setActiveParcelsCount(2);
        shift.setShiftStatus(ShiftStatus.BUSY);

        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> courierShiftService.endShift(courierId, courier(SriLankanDistrict.COLOMBO)))
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
