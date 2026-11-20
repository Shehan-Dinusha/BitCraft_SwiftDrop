package com.swiftdrop.dispatch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.enums.VehicleType;
import com.swiftdrop.dispatch.dto.DispatchAssignmentResponse;
import com.swiftdrop.dispatch.dto.StartShiftRequest;
import com.swiftdrop.dispatch.dto.UpdateShiftStatusRequest;
import com.swiftdrop.dispatch.exception.AssignmentNotFoundException;
import com.swiftdrop.dispatch.service.CourierConflictRetry;
import com.swiftdrop.dispatch.service.CourierShiftService;
import com.swiftdrop.dispatch.service.DispatchAssignmentService;
import com.swiftdrop.dispatch.service.ManualOverrideService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.swiftdrop.dispatch.support.TestCallers.as;
import static com.swiftdrop.dispatch.support.TestCallers.courier;
import static com.swiftdrop.dispatch.support.TestCallers.withIdentity;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Courier-facing endpoints. A courier's dispatch ID is their IAM user ID (the
 * gateway's X-User-Id), so a COURIER may act only on resources whose courier ID
 * equals their user ID; DISPATCHER and SYSTEM_ADMIN may act on any courier;
 * CUSTOMER and MERCHANT are refused even when their user ID equals the courier ID.
 * Rejected requests never reach a service.
 */
@WebMvcTest({DispatchController.class, CourierAvailabilityController.class})
@Import(CourierConflictRetry.class)
class CourierAccessAuthorizationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManualOverrideService manualOverrideService;

    @MockitoBean
    private DispatchAssignmentService dispatchAssignmentService;

    @MockitoBean
    private CourierShiftService courierShiftService;

    /** Endpoints that name the courier in the path or the request body. */
    static Stream<Arguments> courierEndpoints() {
        return Stream.of(
                Arguments.of("GET /couriers/{courierId}", (CourierRequest) id ->
                        get("/api/v1/dispatch/couriers/{courierId}", id)),
                Arguments.of("GET /couriers/{courierId}/assignments", (CourierRequest) id ->
                        get("/api/v1/dispatch/couriers/{courierId}/assignments", id)),
                Arguments.of("POST /couriers/shifts/start", (CourierRequest) id ->
                        jsonBody(post("/api/v1/dispatch/couriers/shifts/start"), new StartShiftRequest(
                                id, "Kamal Silva", null, VehicleType.MOTORCYCLE, SriLankanDistrict.COLOMBO, 5))),
                Arguments.of("POST /couriers/shifts/{courierId}/end", (CourierRequest) id ->
                        post("/api/v1/dispatch/couriers/shifts/{courierId}/end", id)),
                Arguments.of("PUT /couriers/shifts/status", (CourierRequest) id ->
                        jsonBody(put("/api/v1/dispatch/couriers/shifts/status"),
                                new UpdateShiftStatusRequest(id, ShiftStatus.ON_BREAK)))
        );
    }

    static Stream<Arguments> courierEndpointsForOperations() {
        return withRoles(UserRole.DISPATCHER, UserRole.SYSTEM_ADMIN);
    }

    static Stream<Arguments> courierEndpointsForNonCourierRoles() {
        return withRoles(UserRole.CUSTOMER, UserRole.MERCHANT);
    }

    // ── Endpoints keyed by courier ID ──────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("courierEndpoints")
    @DisplayName("A courier can use their own profile, assignments and shift")
    void courierCanUseOwnResources(String endpoint, CourierRequest request) throws Exception {
        UUID courierId = UUID.randomUUID();
        mockMvc.perform(request.build(courierId).with(courier(courierId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("courierEndpoints")
    @DisplayName("A courier gets 403 on another courier's resources and no service is called")
    void courierCannotUseAnotherCouriersResources(String endpoint, CourierRequest request) throws Exception {
        UUID otherCourier = UUID.randomUUID();
        mockMvc.perform(request.build(otherCourier).with(courier(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Caller cannot access this user's resource"));
        assertNoServiceCalled();
    }

    @ParameterizedTest(name = "{2} on {0}")
    @MethodSource("courierEndpointsForOperations")
    @DisplayName("DISPATCHER and SYSTEM_ADMIN can use any courier's resources")
    void operationsCanUseAnyCourier(String endpoint, CourierRequest request, UserRole role) throws Exception {
        mockMvc.perform(request.build(UUID.randomUUID()).with(as(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @ParameterizedTest(name = "{2} on {0}")
    @MethodSource("courierEndpointsForNonCourierRoles")
    @DisplayName("CUSTOMER and MERCHANT get 403 even when their user ID equals the courier ID")
    void nonCourierRolesAreForbiddenEvenForTheirOwnId(String endpoint, CourierRequest request, UserRole role)
            throws Exception {
        UUID sameId = UUID.randomUUID();
        mockMvc.perform(request.build(sameId).with(as(sameId, role)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Caller role is not permitted"));
        assertNoServiceCalled();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("courierEndpoints")
    @DisplayName("Missing or malformed identity headers return 401 and no service is called")
    void missingOrMalformedIdentityIsUnauthorized(String endpoint, CourierRequest request) throws Exception {
        UUID courierId = UUID.randomUUID();
        mockMvc.perform(request.build(courierId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
        mockMvc.perform(request.build(courierId).with(withIdentity("not-a-uuid", "COURIER")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(request.build(courierId).with(withIdentity(courierId.toString(), "courier")))
                .andExpect(status().isUnauthorized());
        assertNoServiceCalled();
    }

    // ── Assignment lookups: ownership is checked after loading ─────────────────

    @Test
    @DisplayName("A courier can look up an assignment they hold, by assignment ID and by order ID")
    void courierCanReadOwnAssignment() throws Exception {
        UUID courierId = UUID.randomUUID();
        DispatchAssignmentResponse assignment = assignment(courierId, AssignmentStatus.ASSIGNED);
        stubLookups(assignment);

        mockMvc.perform(get("/api/v1/dispatch/assignments/{id}", assignment.assignmentId()).with(courier(courierId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courierId").value(courierId.toString()));
        mockMvc.perform(get("/api/v1/dispatch/assignments/order/{orderId}", assignment.orderId())
                        .with(courier(courierId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(assignment.orderId().toString()));
    }

    @Test
    @DisplayName("A courier gets 403 on another courier's assignment")
    void courierCannotReadAnotherCouriersAssignment() throws Exception {
        DispatchAssignmentResponse assignment = assignment(UUID.randomUUID(), AssignmentStatus.ASSIGNED);
        stubLookups(assignment);
        UUID caller = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/dispatch/assignments/{id}", assignment.assignmentId()).with(courier(caller)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Caller cannot access this user's resource"));
        mockMvc.perform(get("/api/v1/dispatch/assignments/order/{orderId}", assignment.orderId())
                        .with(courier(caller)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A courier gets 403 on an UNASSIGNED assignment")
    void courierCannotReadUnassignedAssignment() throws Exception {
        DispatchAssignmentResponse assignment = assignment(null, AssignmentStatus.UNASSIGNED);
        stubLookups(assignment);
        UUID caller = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/dispatch/assignments/{id}", assignment.assignmentId()).with(courier(caller)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dispatch/assignments/order/{orderId}", assignment.orderId())
                        .with(courier(caller)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = UserRole.class, names = {"DISPATCHER", "SYSTEM_ADMIN"})
    @DisplayName("DISPATCHER and SYSTEM_ADMIN can look up any assignment, including UNASSIGNED ones")
    void operationsCanReadAnyAssignment(UserRole role) throws Exception {
        DispatchAssignmentResponse unassigned = assignment(null, AssignmentStatus.UNASSIGNED);
        stubLookups(unassigned);
        mockMvc.perform(get("/api/v1/dispatch/assignments/{id}", unassigned.assignmentId()).with(as(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNASSIGNED"));
        mockMvc.perform(get("/api/v1/dispatch/assignments/order/{orderId}", unassigned.orderId()).with(as(role)))
                .andExpect(status().isOk());

        DispatchAssignmentResponse held = assignment(UUID.randomUUID(), AssignmentStatus.ASSIGNED);
        stubLookups(held);
        mockMvc.perform(get("/api/v1/dispatch/assignments/{id}", held.assignmentId()).with(as(role)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = UserRole.class, names = {"CUSTOMER", "MERCHANT"})
    @DisplayName("CUSTOMER and MERCHANT get 403 on assignment lookups before anything is loaded")
    void nonCourierRolesCannotLookUpAssignments(UserRole role) throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/assignments/{id}", UUID.randomUUID()).with(as(role)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Caller role is not permitted"));
        mockMvc.perform(get("/api/v1/dispatch/assignments/order/{orderId}", UUID.randomUUID()).with(as(role)))
                .andExpect(status().isForbidden());
        assertNoServiceCalled();
    }

    @Test
    @DisplayName("Assignment lookups without identity headers return 401 before anything is loaded")
    void assignmentLookupsWithoutIdentityAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/assignments/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/dispatch/assignments/order/{orderId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        assertNoServiceCalled();
    }

    @Test
    @DisplayName("A courier looking up an assignment that does not exist gets the existing 404")
    void missingAssignmentIsStillNotFound() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        when(dispatchAssignmentService.getAssignmentById(assignmentId))
                .thenThrow(new AssignmentNotFoundException("Assignment not found: " + assignmentId));

        mockMvc.perform(get("/api/v1/dispatch/assignments/{id}", assignmentId).with(courier(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private void stubLookups(DispatchAssignmentResponse assignment) {
        when(dispatchAssignmentService.getAssignmentById(assignment.assignmentId())).thenReturn(assignment);
        when(dispatchAssignmentService.getAssignmentByOrderId(assignment.orderId())).thenReturn(assignment);
    }

    private static DispatchAssignmentResponse assignment(UUID courierId, AssignmentStatus status) {
        DispatchAssignment entity = new DispatchAssignment();
        entity.setAssignmentId(UUID.randomUUID());
        entity.setOrderId(UUID.randomUUID());
        entity.setCourierId(courierId);
        entity.setStatus(status);
        return DispatchAssignmentResponse.fromEntity(entity);
    }

    private void assertNoServiceCalled() {
        verifyNoInteractions(manualOverrideService, dispatchAssignmentService, courierShiftService);
    }

    private static Stream<Arguments> withRoles(UserRole... roles) {
        List<Arguments> cases = new ArrayList<>();
        courierEndpoints().forEach(endpoint -> {
            for (UserRole role : roles) {
                Object[] values = endpoint.get();
                cases.add(Arguments.of(values[0], values[1], role));
            }
        });
        return cases.stream();
    }

    private static MockHttpServletRequestBuilder jsonBody(MockHttpServletRequestBuilder builder, Object body) {
        try {
            return builder.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @FunctionalInterface
    interface CourierRequest {
        MockHttpServletRequestBuilder build(UUID courierId);
    }
}
