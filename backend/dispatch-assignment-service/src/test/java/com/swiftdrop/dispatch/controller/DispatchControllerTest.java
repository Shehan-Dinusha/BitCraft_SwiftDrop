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
import com.swiftdrop.dispatch.service.DispatchAssignmentService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DispatchController.class)
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

        when(manualOverrideService.manualAssign(any(ManualAssignRequest.class), any())).thenReturn(assignment);

        mockMvc.perform(post("/api/v1/dispatch/assign")
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
        );

        when(dispatchAssignmentService.getUnassignedParcels()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/dispatch/unassigned"))
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

        mockMvc.perform(get("/api/v1/dispatch/orders/{orderId}/suggestions", orderId))
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

        when(manualOverrideService.manualAssign(any(ManualAssignRequest.class), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(DispatchAssignment.class, 1L));

        mockMvc.perform(post("/api/v1/dispatch/assign")
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
        when(manualOverrideService.manualAssign(any(ManualAssignRequest.class), any()))
                .thenThrow(new AssignmentConflictException("Order is already assigned to a courier."));

        mockMvc.perform(post("/api/v1/dispatch/assign")
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
        when(manualOverrideService.reassign(any(ReassignCourierRequest.class), any())).thenReturn(assignment);

        mockMvc.perform(post("/api/v1/dispatch/reassign")
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

        mockMvc.perform(post("/api/v1/dispatch/reassign")
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
        when(manualOverrideService.reassign(any(ReassignCourierRequest.class), any())).thenReturn(reassigned());

        mockMvc.perform(post("/api/v1/dispatch/reassign")
                        .header("X-Correlation-Id", correlationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reassignRequest())))
                .andExpect(status().isOk());

        verify(manualOverrideService).reassign(any(ReassignCourierRequest.class), eq(correlationId));
    }

    @Test
    @DisplayName("A non-UUID X-Correlation-Id is not propagated and does not fail the request")
    void testReassign_IgnoresNonUuidCorrelationId() throws Exception {
        when(manualOverrideService.reassign(any(ReassignCourierRequest.class), any())).thenReturn(reassigned());

        mockMvc.perform(post("/api/v1/dispatch/reassign")
                        .header("X-Correlation-Id", "client-trace-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reassignRequest())))
                .andExpect(status().isOk());

        verify(manualOverrideService).reassign(any(ReassignCourierRequest.class), isNull());
    }

    @Test
    @DisplayName("POST /api/v1/dispatch/assign without X-Correlation-Id lets the service generate one")
    void testManualAssign_WithoutCorrelationId() throws Exception {
        DispatchAssignment assignment = reassigned();
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        when(manualOverrideService.manualAssign(any(ManualAssignRequest.class), any())).thenReturn(assignment);

        mockMvc.perform(post("/api/v1/dispatch/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ManualAssignRequest(UUID.randomUUID(), UUID.randomUUID(), "DISPATCHER_ONE", null))))
                .andExpect(status().isOk());

        verify(manualOverrideService).manualAssign(any(ManualAssignRequest.class), isNull());
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
}
