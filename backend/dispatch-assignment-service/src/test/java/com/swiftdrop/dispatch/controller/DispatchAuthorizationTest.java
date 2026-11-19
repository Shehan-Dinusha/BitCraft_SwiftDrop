package com.swiftdrop.dispatch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.dto.ManualAssignRequest;
import com.swiftdrop.dispatch.dto.ReassignCourierRequest;
import com.swiftdrop.dispatch.service.CourierConflictRetry;
import com.swiftdrop.dispatch.service.CourierShiftService;
import com.swiftdrop.dispatch.service.DispatchAssignmentService;
import com.swiftdrop.dispatch.service.ManualOverrideService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.swiftdrop.dispatch.support.TestCallers.as;
import static com.swiftdrop.dispatch.support.TestCallers.withIdentity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dispatch-management endpoints are limited to DISPATCHER and SYSTEM_ADMIN,
 * using the identity headers the API gateway sets after verifying the JWT.
 * A rejected caller never reaches a service, so nothing is read or changed.
 */
@WebMvcTest({DispatchController.class, CourierAvailabilityController.class})
@Import(CourierConflictRetry.class)
class DispatchAuthorizationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManualOverrideService manualOverrideService;

    @MockitoBean
    private DispatchAssignmentService dispatchAssignmentService;

    @MockitoBean
    private CourierShiftService courierShiftService;

    @BeforeEach
    void stubSuccessfulOperations() {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setAssignmentId(UUID.randomUUID());
        assignment.setOrderId(UUID.randomUUID());
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        when(manualOverrideService.manualAssign(any(ManualAssignRequest.class), any())).thenReturn(assignment);
        when(manualOverrideService.reassign(any(ReassignCourierRequest.class), any())).thenReturn(assignment);
        when(dispatchAssignmentService.getAssignments(any(), any())).thenReturn(Page.empty());
    }

    /** Every endpoint restricted to the operations roles, as a fresh request builder. */
    static Stream<Arguments> operationsEndpoints() {
        return Stream.of(
                Arguments.of("POST /assign", (EndpointRequest) () -> post("/api/v1/dispatch/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ManualAssignRequest(UUID.randomUUID(), UUID.randomUUID(), "D1", null)))),
                Arguments.of("POST /reassign", (EndpointRequest) () -> post("/api/v1/dispatch/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReassignCourierRequest(UUID.randomUUID(), UUID.randomUUID(),
                                UUID.randomUUID(), "D1", null)))),
                Arguments.of("GET /orders/{orderId}/suggestions", (EndpointRequest) () ->
                        get("/api/v1/dispatch/orders/{orderId}/suggestions", UUID.randomUUID())),
                Arguments.of("GET /unassigned", (EndpointRequest) () -> get("/api/v1/dispatch/unassigned")),
                Arguments.of("GET /assignments", (EndpointRequest) () -> get("/api/v1/dispatch/assignments")),
                Arguments.of("GET /couriers", (EndpointRequest) () -> get("/api/v1/dispatch/couriers")),
                Arguments.of("GET /couriers/workload", (EndpointRequest) () -> get("/api/v1/dispatch/couriers/workload"))
        );
    }

    static Stream<Arguments> operationsRoleCases() {
        return withRoles(UserRole.DISPATCHER, UserRole.SYSTEM_ADMIN);
    }

    static Stream<Arguments> otherRoleCases() {
        return withRoles(UserRole.COURIER, UserRole.CUSTOMER, UserRole.MERCHANT);
    }

    @ParameterizedTest(name = "{2} may call {0}")
    @MethodSource("operationsRoleCases")
    @DisplayName("DISPATCHER and SYSTEM_ADMIN can call every dispatch-management endpoint")
    void operationsRolesAreAllowed(String endpoint, EndpointRequest request, UserRole role) throws Exception {
        mockMvc.perform(request.build().with(as(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @ParameterizedTest(name = "{2} is forbidden from {0}")
    @MethodSource("otherRoleCases")
    @DisplayName("COURIER, CUSTOMER and MERCHANT get 403 and no service is called")
    void otherRolesAreForbidden(String endpoint, EndpointRequest request, UserRole role) throws Exception {
        mockMvc.perform(request.build().with(as(role)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Caller role is not permitted"));
        assertNoServiceCalled();
    }

    @ParameterizedTest(name = "{0} without identity headers")
    @MethodSource("operationsEndpoints")
    @DisplayName("Missing identity headers return 401 and no service is called")
    void missingIdentityIsUnauthorized(String endpoint, EndpointRequest request) throws Exception {
        mockMvc.perform(request.build())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
        assertNoServiceCalled();
    }

    @ParameterizedTest(name = "{0} with a malformed identity")
    @MethodSource("operationsEndpoints")
    @DisplayName("A user ID that is not a UUID returns 401")
    void malformedUserIdIsUnauthorized(String endpoint, EndpointRequest request) throws Exception {
        mockMvc.perform(request.build().with(withIdentity("not-a-uuid", "DISPATCHER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid " + JwtHeaderConstants.X_USER_ID + " header"));
        assertNoServiceCalled();
    }

    @Test
    @DisplayName("Unknown, alias or lower-case roles return 401 rather than being promoted")
    void unrecognisedRolesAreUnauthorized() throws Exception {
        for (String role : List.of("ADMIN_OPS", "dispatcher", "SUPERUSER")) {
            mockMvc.perform(post("/api/v1/dispatch/assign")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ManualAssignRequest(UUID.randomUUID(), UUID.randomUUID(), "D1", null)))
                            .with(withIdentity(UUID.randomUUID().toString(), role)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid " + JwtHeaderConstants.X_USER_ROLE + " header"));
        }
        assertNoServiceCalled();
    }

    @Test
    @DisplayName("A user ID without a role (or a role without a user ID) returns 401")
    void partialIdentityIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/unassigned")
                        .header(JwtHeaderConstants.X_USER_ROLE, "DISPATCHER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing " + JwtHeaderConstants.X_USER_ID + " header"));
        mockMvc.perform(get("/api/v1/dispatch/unassigned")
                        .header(JwtHeaderConstants.X_USER_ID, UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing " + JwtHeaderConstants.X_USER_ROLE + " header"));
        assertNoServiceCalled();
    }

    @Test
    @DisplayName("Health endpoints stay open without identity headers")
    void healthEndpointsNeedNoIdentity() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/dispatch/couriers/health")).andExpect(status().isOk());
    }

    private void assertNoServiceCalled() {
        verifyNoInteractions(manualOverrideService, courierShiftService);
        verifyNoInteractions(dispatchAssignmentService);
    }

    private static Stream<Arguments> withRoles(UserRole... roles) {
        List<Arguments> cases = new ArrayList<>();
        operationsEndpoints().forEach(endpoint -> {
            for (UserRole role : roles) {
                Object[] values = endpoint.get();
                cases.add(Arguments.of(values[0], values[1], role));
            }
        });
        return cases.stream();
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @FunctionalInterface
    interface EndpointRequest {
        MockHttpServletRequestBuilder build();
    }
}
