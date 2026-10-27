package com.swiftdrop.dispatch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.dto.DispatchAssignmentResponse;
import com.swiftdrop.dispatch.dto.ManualAssignRequest;
import com.swiftdrop.dispatch.service.DispatchAssignmentService;
import com.swiftdrop.dispatch.service.ManualOverrideService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @MockBean
    private ManualOverrideService manualOverrideService;

    @MockBean
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

        when(manualOverrideService.manualAssign(any(ManualAssignRequest.class))).thenReturn(assignment);

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
}
