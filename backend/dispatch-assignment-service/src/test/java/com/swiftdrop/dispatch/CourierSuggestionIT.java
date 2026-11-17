package com.swiftdrop.dispatch;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.dto.CourierSuggestionResponse;
import com.swiftdrop.dispatch.exception.AssignmentNotFoundException;
import com.swiftdrop.dispatch.exception.InvalidAssignmentTransitionException;
import com.swiftdrop.dispatch.service.DispatchAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Courier suggestions against PostgreSQL, so the eligibility rules in
 * {@code CourierShiftRepository.findAvailableCandidates} (AVAILABLE and below
 * capacity) run as real SQL on Flyway-migrated tables. Each test runs in a
 * transaction that is rolled back, and starts from empty tables.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DispatchAssignmentService.class)
@Testcontainers(disabledWithoutDocker = true)
class CourierSuggestionIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DispatchAssignmentService dispatchAssignmentService;

    @Autowired
    private DispatchAssignmentRepository assignmentRepository;

    @Autowired
    private CourierShiftRepository courierShiftRepository;

    @BeforeEach
    void startFromEmptyTables() {
        assignmentRepository.deleteAll();
        courierShiftRepository.deleteAll();
    }

    @Test
    @DisplayName("Only AVAILABLE couriers below capacity are suggested: OFFLINE, ON_BREAK, BUSY and full are not")
    void onlyAvailableCouriersBelowCapacityAreSuggested() {
        UUID eligible = courier(SriLankanDistrict.COLOMBO, ShiftStatus.AVAILABLE, 4, 5, null);
        courier(SriLankanDistrict.COLOMBO, ShiftStatus.OFFLINE, 0, 5, null);
        courier(SriLankanDistrict.COLOMBO, ShiftStatus.ON_BREAK, 0, 5, null);
        courier(SriLankanDistrict.COLOMBO, ShiftStatus.BUSY, 2, 5, null);
        courier(SriLankanDistrict.COLOMBO, ShiftStatus.AVAILABLE, 5, 5, null); // full but still AVAILABLE
        UUID orderId = order(AssignmentStatus.UNASSIGNED, null, SriLankanDistrict.COLOMBO);

        assertThat(courierIds(dispatchAssignmentService.getSuggestions(orderId))).containsExactly(eligible);
    }

    @Test
    @DisplayName("Ranking: pickup district, then workload, then latest heartbeat (none last), then courier ID; three at most")
    void rankingAndLimitOnRealData() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID kandyIdle = courier(SriLankanDistrict.KANDY, ShiftStatus.AVAILABLE, 0, 5, now);
        UUID colomboBusier = courier(SriLankanDistrict.COLOMBO, ShiftStatus.AVAILABLE, 3, 5, now);
        UUID colomboNoHeartbeat = courier(SriLankanDistrict.COLOMBO, ShiftStatus.AVAILABLE, 1, 5, null);
        UUID colomboOlderHeartbeat = courier(SriLankanDistrict.COLOMBO, ShiftStatus.AVAILABLE, 1, 5, now.minusSeconds(300));
        UUID colomboRecentHeartbeat = courier(SriLankanDistrict.COLOMBO, ShiftStatus.AVAILABLE, 1, 5, now.minusSeconds(5));
        UUID orderId = order(AssignmentStatus.UNASSIGNED, null, SriLankanDistrict.COLOMBO);

        List<CourierSuggestionResponse> suggestions = dispatchAssignmentService.getSuggestions(orderId);

        assertThat(courierIds(suggestions))
                .containsExactly(colomboRecentHeartbeat, colomboOlderHeartbeat, colomboNoHeartbeat)
                .doesNotContain(colomboBusier, kandyIdle);
        assertThat(suggestions).allSatisfy(s -> assertThat(s.samePickupDistrict()).isTrue());
    }

    @Test
    @DisplayName("Couriers equal on district, workload and heartbeat are ordered by courier ID")
    void courierIdBreaksFullTies() {
        Instant heartbeat = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID high = courier(UUID.fromString("00000000-0000-4000-8000-0000000000b2"),
                SriLankanDistrict.COLOMBO, ShiftStatus.AVAILABLE, 1, 5, heartbeat);
        UUID low = courier(UUID.fromString("00000000-0000-4000-8000-0000000000a1"),
                SriLankanDistrict.COLOMBO, ShiftStatus.AVAILABLE, 1, 5, heartbeat);
        UUID orderId = order(AssignmentStatus.UNASSIGNED, null, SriLankanDistrict.COLOMBO);

        assertThat(courierIds(dispatchAssignmentService.getSuggestions(orderId))).containsExactly(low, high);
    }

    @Test
    @DisplayName("Reassignment suggestions for ASSIGNED and REASSIGNED orders leave out the current courier")
    void currentCourierIsLeftOutForAssignedOrders() {
        UUID current = courier(SriLankanDistrict.COLOMBO, ShiftStatus.AVAILABLE, 0, 5, null);
        UUID other = courier(SriLankanDistrict.COLOMBO, ShiftStatus.AVAILABLE, 2, 5, null);
        UUID assigned = order(AssignmentStatus.ASSIGNED, current, SriLankanDistrict.COLOMBO);
        UUID reassigned = order(AssignmentStatus.REASSIGNED, current, SriLankanDistrict.COLOMBO);

        assertThat(courierIds(dispatchAssignmentService.getSuggestions(assigned))).containsExactly(other);
        assertThat(courierIds(dispatchAssignmentService.getSuggestions(reassigned))).containsExactly(other);
    }

    @Test
    @DisplayName("COMPLETED and CANCELLED orders get no suggestions; an unknown order is not found")
    void terminalAndUnknownOrdersAreRejected() {
        courier(SriLankanDistrict.COLOMBO, ShiftStatus.AVAILABLE, 0, 5, null);
        UUID completed = order(AssignmentStatus.COMPLETED, null, SriLankanDistrict.COLOMBO);
        UUID cancelled = order(AssignmentStatus.CANCELLED, null, SriLankanDistrict.COLOMBO);

        assertThatThrownBy(() -> dispatchAssignmentService.getSuggestions(completed))
                .isInstanceOf(InvalidAssignmentTransitionException.class);
        assertThatThrownBy(() -> dispatchAssignmentService.getSuggestions(cancelled))
                .isInstanceOf(InvalidAssignmentTransitionException.class);
        assertThatThrownBy(() -> dispatchAssignmentService.getSuggestions(UUID.randomUUID()))
                .isInstanceOf(AssignmentNotFoundException.class);
    }

    @Test
    @DisplayName("No eligible courier gives an empty list")
    void noEligibleCourierGivesEmptyList() {
        courier(SriLankanDistrict.COLOMBO, ShiftStatus.OFFLINE, 0, 5, null);
        UUID orderId = order(AssignmentStatus.UNASSIGNED, null, SriLankanDistrict.COLOMBO);

        assertThat(dispatchAssignmentService.getSuggestions(orderId)).isEmpty();
    }

    @Test
    @DisplayName("An order without a pickup district ranks couriers by workload alone")
    void orderWithoutPickupDistrict() {
        UUID kandyIdle = courier(SriLankanDistrict.KANDY, ShiftStatus.AVAILABLE, 0, 5, null);
        UUID colomboBusy = courier(SriLankanDistrict.COLOMBO, ShiftStatus.AVAILABLE, 2, 5, null);
        UUID orderId = order(AssignmentStatus.UNASSIGNED, null, null);

        List<CourierSuggestionResponse> suggestions = dispatchAssignmentService.getSuggestions(orderId);

        assertThat(courierIds(suggestions)).containsExactly(kandyIdle, colomboBusy);
        assertThat(suggestions).allSatisfy(s -> assertThat(s.samePickupDistrict()).isFalse());
    }

    private static List<UUID> courierIds(List<CourierSuggestionResponse> suggestions) {
        return suggestions.stream().map(CourierSuggestionResponse::courierId).toList();
    }

    private UUID order(AssignmentStatus status, UUID courierId, SriLankanDistrict pickupDistrict) {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(UUID.randomUUID());
        assignment.setTrackingNumber("SD-IT-" + UUID.randomUUID().toString().substring(0, 8));
        assignment.setPickupDistrict(pickupDistrict);
        assignment.setDeliveryDistrict(SriLankanDistrict.GAMPAHA);
        assignment.setStatus(status);
        assignment.setCourierId(courierId);
        return assignmentRepository.saveAndFlush(assignment).getOrderId();
    }

    private UUID courier(SriLankanDistrict district, ShiftStatus status, int active, int capacity, Instant heartbeat) {
        return courier(UUID.randomUUID(), district, status, active, capacity, heartbeat);
    }

    private UUID courier(UUID courierId, SriLankanDistrict district, ShiftStatus status,
                         int active, int capacity, Instant heartbeat) {
        CourierShift courier = new CourierShift();
        courier.setCourierId(courierId);
        courier.setCourierName("IT Courier");
        courier.setCurrentDistrict(district);
        courier.setShiftStatus(status);
        courier.setActiveParcelsCount(active);
        courier.setMaxCapacity(capacity);
        courier.setLastHeartbeatAt(heartbeat);
        return courierShiftRepository.saveAndFlush(courier).getCourierId();
    }
}
