package com.swiftdrop.dispatch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.enums.VehicleType;
import com.swiftdrop.dispatch.dto.CourierLocationPingRequest;
import com.swiftdrop.dispatch.dto.CourierShiftResponse;
import com.swiftdrop.dispatch.dto.StartShiftRequest;
import com.swiftdrop.dispatch.dto.UpdateShiftStatusRequest;
import com.swiftdrop.dispatch.service.CourierShiftService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourierAvailabilityController.class)
class CourierAvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CourierShiftService courierShiftService;

    @Test
    @DisplayName("GET /api/v1/dispatch/couriers/health returns UP")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/couriers/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("UP"));
    }

    @Test
    @DisplayName("POST /api/v1/dispatch/couriers/shifts/start starts shift successfully")
    void testStartShift() throws Exception {
        UUID courierId = UUID.randomUUID();
        StartShiftRequest request = new StartShiftRequest(
                courierId,
                "Kamal Silva",
                "+94772345678",
                VehicleType.MOTORCYCLE,
                SriLankanDistrict.COLOMBO,
                5
        );

        CourierShiftResponse response = new CourierShiftResponse(
                courierId,
                "Kamal Silva",
                "+94772345678",
                VehicleType.MOTORCYCLE,
                SriLankanDistrict.COLOMBO,
                null,
                null,
                ShiftStatus.AVAILABLE,
                0,
                5,
                Instant.now(),
                null,
                Instant.now()
        );

        when(courierShiftService.startShift(any(StartShiftRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/dispatch/couriers/shifts/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.courierName").value("Kamal Silva"))
                .andExpect(jsonPath("$.data.shiftStatus").value("AVAILABLE"));
    }

    @Test
    @DisplayName("PUT /api/v1/dispatch/couriers/shifts/status updates shift status")
    void testUpdateShiftStatus() throws Exception {
        UUID courierId = UUID.randomUUID();
        UpdateShiftStatusRequest request = new UpdateShiftStatusRequest(courierId, ShiftStatus.ON_BREAK);

        CourierShiftResponse response = new CourierShiftResponse(
                courierId,
                "Kamal Silva",
                "+94772345678",
                VehicleType.MOTORCYCLE,
                SriLankanDistrict.COLOMBO,
                null,
                null,
                ShiftStatus.ON_BREAK,
                0,
                5,
                Instant.now(),
                null,
                Instant.now()
        );

        when(courierShiftService.updateStatus(eq(courierId), eq(ShiftStatus.ON_BREAK))).thenReturn(response);

        mockMvc.perform(put("/api/v1/dispatch/couriers/shifts/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shiftStatus").value("ON_BREAK"));
    }

    @Test
    @DisplayName("PUT /api/v1/dispatch/couriers/shifts/location updates heartbeat ping")
    void testLocationPing() throws Exception {
        UUID courierId = UUID.randomUUID();
        CourierLocationPingRequest request = new CourierLocationPingRequest(
                courierId,
                SriLankanDistrict.COLOMBO,
                BigDecimal.valueOf(6.9271),
                BigDecimal.valueOf(79.8612)
        );

        CourierShiftResponse response = new CourierShiftResponse(
                courierId,
                "Kamal Silva",
                "+94772345678",
                VehicleType.MOTORCYCLE,
                SriLankanDistrict.COLOMBO,
                BigDecimal.valueOf(6.9271),
                BigDecimal.valueOf(79.8612),
                ShiftStatus.AVAILABLE,
                0,
                5,
                Instant.now(),
                null,
                Instant.now()
        );

        when(courierShiftService.updateLocation(any(CourierLocationPingRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/dispatch/couriers/shifts/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.latitude").value(6.9271))
                .andExpect(jsonPath("$.data.longitude").value(79.8612));
    }
}
