package com.swiftdrop.dispatch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.dto.DispatchAssignmentResponse;
import com.swiftdrop.dispatch.dto.CourierSuggestionResponse;
import com.swiftdrop.dispatch.dto.ManualAssignRequest;
import com.swiftdrop.dispatch.dto.ReassignCourierRequest;
import com.swiftdrop.dispatch.exception.AssignmentConflictException;
import com.swiftdrop.dispatch.exception.ReassignmentNotAllowedException;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.service.CourierConflictRetry;
import com.swiftdrop.dispatch.service.DispatchAssignmentService;
import org.springframework.context.annotation.Import;
import com.swiftdrop.dispatch.service.ManualOverrideService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.swiftdrop.dispatch.support.TestCallers.dispatcher;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DispatchController.class)
@Import(CourierConflictRetry.class)
class DispatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ManualOverrideService manualOverrideService;

    @MockitoBean
    private DispatchAssignmentService dispatchAssignmentService;

    @Test
    @DisplayName("GET /api/v1/dispatch/health returns UP")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("UP"));
    }

    @Test
    @DisplayName("POST /api/v1/dispatch/assign triggers manual assign override")
    void testManualAssign() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        ManualAssignRequest request = new ManualAssignRequest(orderId, courierId, "ADMIN", "Urgent VIP order");

        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setAssignmentId(UUID.randomUUID());
        assignment.setOrderId(orderId);
        assignment.setCourierId(courierId);
        assignment.setCourierName("Sunil Perera");
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setPickupDistrict(SriLankanDistrict.COLOMBO);
        assignment.setDeliveryDistrict(SriLankanDistrict.GAMPAHA);
        assignment.setOrderSpeed(OrderSpeed.EXPRESS);
        assignment.setAssignedBy("ADMIN");
        assignment.setAssignedAt(Instant.now());

        when(manualOverrideService.manualAssign(any(ManualAssignRequest.class), any(), any())).thenReturn(assignment);

        mockMvc.perform(post("/api/v1/dispatch/assign").with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.courierName").value("Sunil Perera"))
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"));
    }

    @Test
    @DisplayName("GET /api/v1/dispatch/unassigned returns pending list")
    void testGetUnassigned() throws Exception {
        DispatchAssignmentResponse item = new DispatchAssignmentResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SD-COL-99999",
                null,
                null,
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.KANDY,
                OrderSpeed.STANDARD,
                AssignmentStatus.UNASSIGNED,
                "AUTO_ENGINE",
                null,
                30,
                "No courier available",
                1,
                Instant.now(),
                Instant.now()
        , null, null, null);

        when(dispatchAssignmentService.getUnassignedParcels(any())).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/dispatch/unassigned").with(dispatcher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].status").value("UNASSIGNED"))
                .andExpect(jsonPath("$.data[0].pickupDistrict").value("COLOMBO"));
    }

    @Test
    @DisplayName("GET /api/v1/dispatch/orders/{orderId}/suggestions returns ranked couriers")
    void testGetCourierSuggestions() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        CourierSuggestionResponse suggestion = new CourierSuggestionResponse(
                courierId,
                "Sunil Perera",
                SriLankanDistrict.COLOMBO,
                true,
                1,
                5,
                com.swiftdrop.dispatch.domain.enums.ShiftStatus.AVAILABLE
        );
        when(dispatchAssignmentService.getSuggestions(orderId)).thenReturn(List.of(suggestion));
        DispatchAssignment order = new DispatchAssignment();
        order.setOrderId(orderId);
        order.setPickupDistrict(com.swiftdrop.contracts.enums.SriLankanDistrict.COLOMBO);
        when(dispatchAssignmentService.getAssignmentByOrderId(orderId))
                .thenReturn(com.swiftdrop.dispatch.dto.DispatchAssignmentResponse.fromEntity(order));

        mockMvc.perform(get("/api/v1/dispatch/orders/{orderId}/suggestions", orderId).with(dispatcher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].courierId").value(courierId.toString()))
                .andExpect(jsonPath("$.data[0].samePickupDistrict").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/dispatch/assign returns 409 after concurrent update")
    void testManualAssign_OptimisticLockConflict() throws Exception {
        ManualAssignRequest request = new ManualAssignRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ADMIN",
                "Retry after conflict"
        );

        when(manualOverrideService.manualAssign(any(ManualAssignRequest.class), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(DispatchAssignment.class, 1L));

        mockMvc.perform(post("/api/v1/dispatch/assign").with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        "The dispatch record was changed by another operation. Refresh and retry."
                ));
    }

    @Test
    @DisplayName("POST /api/v1/dispatch/assign returns 409 when the order is already assigned")
    void testManualAssign_AlreadyAssignedConflict() throws Exception {
        ManualAssignRequest request = new ManualAssignRequest(UUID.randomUUID(), UUID.randomUUID(), "DISPATCHER_TWO", null);
        when(manualOverrideService.manualAssign(any(ManualAssignRequest.class), any(), any()))
                .thenThrow(new AssignmentConflictException("Order is already assigned to a courier."));

        mockMvc.perform(post("/api/v1/dispatch/assign").with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Assignment Conflict"))
                .andExpect(jsonPath("$.message").value("Order is already assigned to a courier."));
    }

    @Test
    @DisplayName("POST /api/v1/dispatch/reassign moves the order to the new courier")
    void testReassign() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID newCourierId = UUID.randomUUID();
        ReassignCourierRequest request = new ReassignCourierRequest(
                orderId, UUID.randomUUID(), newCourierId, "DISPATCH_SUPERVISOR", "Courier vehicle breakdown");

        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setAssignmentId(UUID.randomUUID());
        assignment.setOrderId(orderId);
        assignment.setCourierId(newCourierId);
        assignment.setCourierName("Kamal Silva");
        assignment.setStatus(AssignmentStatus.REASSIGNED);
        when(manualOverrideService.reassign(any(ReassignCourierRequest.class), any(), any())).thenReturn(assignment);

        mockMvc.perform(post("/api/v1/dispatch/reassign").with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.courierId").value(newCourierId.toString()))
                .andExpect(jsonPath("$.data.status").value("REASSIGNED"));
    }

    @Test
    @DisplayName("POST /api/v1/dispatch/reassign requires the expected current courier")
    void testReassign_MissingExpectedCourierIsRejected() throws Exception {
        ReassignCourierRequest request = new ReassignCourierRequest(
                UUID.randomUUID(), null, UUID.randomUUID(), "DISPATCH_SUPERVISOR", null);

        mockMvc.perform(post("/api/v1/dispatch/reassign").with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(manualOverrideService);
    }

    @Test
    @DisplayName("POST /api/v1/dispatch/reassign passes a UUID X-Correlation-Id to the service")
    void testReassign_PropagatesCorrelationId() throws Exception {
        UUID correlationId = UUID.randomUUID();
        when(manualOverrideService.reassign(any(ReassignCourierRequest.class), any(), any())).thenReturn(reassigned());

        mockMvc.perform(post("/api/v1/dispatch/reassign").with(dispatcher())
                        .header("X-Correlation-Id", correlationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reassignRequest())))
                .andExpect(status().isOk());

        verify(manualOverrideService).reassign(any(ReassignCourierRequest.class), eq(correlationId), any());
    }

    @Test
    @DisplayName("A non-UUID X-Correlation-Id is not propagated and does not fail the request")
    void testReassign_IgnoresNonUuidCorrelationId() throws Exception {
        when(manualOverrideService.reassign(any(ReassignCourierRequest.class), any(), any())).thenReturn(reassigned());

        mockMvc.perform(post("/api/v1/dispatch/reassign").with(dispatcher())
                        .header("X-Correlation-Id", "client-trace-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reassignRequest())))
                .andExpect(status().isOk());

        verify(manualOverrideService).reassign(any(ReassignCourierRequest.class), isNull(), any());
    }

    @Test
    @DisplayName("POST /api/v1/dispatch/assign without X-Correlation-Id lets the service generate one")
    void testManualAssign_WithoutCorrelationId() throws Exception {
        DispatchAssignment assignment = reassigned();
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        when(manualOverrideService.manualAssign(any(ManualAssignRequest.class), any(), any())).thenReturn(assignment);

        mockMvc.perform(post("/api/v1/dispatch/assign").with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ManualAssignRequest(UUID.randomUUID(), UUID.randomUUID(), "DISPATCHER_ONE", null))))
                .andExpect(status().isOk());

        verify(manualOverrideService).manualAssign(any(ManualAssignRequest.class), isNull(), any());
    }

    @Test
    @DisplayName("POST /api/v1/dispatch/reassign returns 409 Reassignment Not Allowed after pickup")
    void testReassign_AfterPickupConflict() throws Exception {
        when(manualOverrideService.reassign(any(ReassignCourierRequest.class), any(), any()))
                .thenThrow(new ReassignmentNotAllowedException("Order was picked up; reassignment is not allowed after pickup."));

        mockMvc.perform(post("/api/v1/dispatch/reassign").with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reassignRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Reassignment Not Allowed"))
                .andExpect(jsonPath("$.message").value("Order was picked up; reassignment is not allowed after pickup."));
    }

    private ReassignCourierRequest reassignRequest() {
        return new ReassignCourierRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "DISPATCH_SUPERVISOR", null);
    }

    private DispatchAssignment reassigned() {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setAssignmentId(UUID.randomUUID());
        assignment.setOrderId(UUID.randomUUID());
        assignment.setCourierId(UUID.randomUUID());
        assignment.setStatus(AssignmentStatus.REASSIGNED);
        return assignment;
    }

    @Test
    @DisplayName("Unexpected errors return a generic 500 without internal details, echoing the correlation ID")
    void testUnexpectedErrorDoesNotLeakDetails() throws Exception {
        UUID correlationId = UUID.randomUUID();
        when(manualOverrideService.manualAssign(any(ManualAssignRequest.class), any(), any()))
                .thenThrow(new IllegalStateException(
                        "could not execute statement [ERROR: relation courier_shifts phone +94771234567]"));

        mockMvc.perform(post("/api/v1/dispatch/assign").with(dispatcher())
                        .header("X-Correlation-Id", correlationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignRequest())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.correlationId").value(correlationId.toString()))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("courier_shifts", "+94771234567", "statement"));
    }

    @Test
    @DisplayName("A non-UUID correlation header is not reflected in the 500 response")
    void testUnexpectedErrorIgnoresNonUuidCorrelationHeader() throws Exception {
        when(manualOverrideService.manualAssign(any(ManualAssignRequest.class), any(), any()))
                .thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(post("/api/v1/dispatch/assign").with(dispatcher())
                        .header("X-Correlation-Id", "<script>alert(1)</script>")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignRequest())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.correlationId").doesNotExist());
    }

    @Test
    @DisplayName("An assignment that loses a courier-record race once is retried and succeeds")
    void testManualAssign_CourierConflictIsRetried() throws Exception {
        DispatchAssignment assignment = reassigned();
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        when(manualOverrideService.manualAssign(any(ManualAssignRequest.class), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(CourierShift.class, 1L))
                .thenReturn(assignment);

        mockMvc.perform(post("/api/v1/dispatch/assign").with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"));

        verify(manualOverrideService, times(2)).manualAssign(any(ManualAssignRequest.class), any(), any());
    }

    @Test
    @DisplayName("Persistent courier-record conflicts end in 409 with a courier-specific message")
    void testManualAssign_CourierConflictExhaustedReturnsCourierMessage() throws Exception {
        when(manualOverrideService.manualAssign(any(ManualAssignRequest.class), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(CourierShift.class, 1L));

        mockMvc.perform(post("/api/v1/dispatch/assign").with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        "The courier's record was changed by another operation, such as a status update or "
                                + "another assignment. Refresh and retry."));

        verify(manualOverrideService, times(3)).manualAssign(any(ManualAssignRequest.class), any(), any());
    }

    @Test
    @DisplayName("A conflict on the assignment itself (another dispatcher) is not retried")
    void testManualAssign_AssignmentConflictIsNotRetried() throws Exception {
        when(manualOverrideService.manualAssign(any(ManualAssignRequest.class), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(DispatchAssignment.class, 1L));

        mockMvc.perform(post("/api/v1/dispatch/assign").with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "The dispatch record was changed by another operation. Refresh and retry."));

        verify(manualOverrideService, times(1)).manualAssign(any(ManualAssignRequest.class), any(), any());
    }

    private ManualAssignRequest assignRequest() {
        return new ManualAssignRequest(UUID.randomUUID(), UUID.randomUUID(), "DISPATCHER_ONE", null);
    }
}
