package com.swiftdrop.dispatch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.enums.VehicleType;
import com.swiftdrop.dispatch.dto.CourierShiftResponse;
import com.swiftdrop.dispatch.dto.CourierWorkloadResponse;
import com.swiftdrop.dispatch.dto.StartShiftRequest;
import com.swiftdrop.dispatch.dto.UpdateShiftStatusRequest;
import com.swiftdrop.dispatch.exception.ShiftEndNotAllowedException;
import com.swiftdrop.dispatch.service.CourierShiftService;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import static com.swiftdrop.dispatch.support.TestCallers.courier;
import static com.swiftdrop.dispatch.support.TestCallers.dispatcher;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
                SriLankanDistrict.COLOMBO
        );

        CourierShiftResponse response = new CourierShiftResponse(
                courierId,
                "Kamal Silva",
                "+94772345678",
                VehicleType.MOTORCYCLE,
                SriLankanDistrict.COLOMBO,
                ShiftStatus.AVAILABLE,
                0,
                5,
                Instant.now(),
                null,
                Instant.now(),
                java.math.BigDecimal.ZERO,
                25,
                java.math.BigDecimal.ZERO,
                100
        );

        when(courierShiftService.startShift(any(StartShiftRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/dispatch/couriers/shifts/start").with(courier(courierId))
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
                ShiftStatus.ON_BREAK,
                0,
                5,
                Instant.now(),
                null,
                Instant.now(),
                java.math.BigDecimal.ZERO,
                25,
                java.math.BigDecimal.ZERO,
                100
        );

        when(courierShiftService.updateStatus(eq(courierId), eq(ShiftStatus.ON_BREAK))).thenReturn(response);

        mockMvc.perform(put("/api/v1/dispatch/couriers/shifts/status").with(courier(courierId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shiftStatus").value("ON_BREAK"));
    }

    @Test
    @DisplayName("GET /api/v1/dispatch/couriers/workload returns workload metrics")
    void testGetWorkloads() throws Exception {
        UUID courierId = UUID.randomUUID();
        CourierWorkloadResponse workload = new CourierWorkloadResponse(
                courierId,
                "Kamal Silva",
                2,
                5,
                SriLankanDistrict.COLOMBO,
                ShiftStatus.AVAILABLE
        );
        when(courierShiftService.getWorkloads(any())).thenReturn(List.of(workload));

        mockMvc.perform(get("/api/v1/dispatch/couriers/workload").with(dispatcher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].courierId").value(courierId.toString()))
                .andExpect(jsonPath("$.data[0].activeParcels").value(2));
    }

    @Test
    @DisplayName("POST /shifts/{courierId}/end returns 409 Shift Conflict while active parcels remain")
    void testEndShift_ActiveParcelsConflict() throws Exception {
        UUID courierId = UUID.randomUUID();
        when(courierShiftService.endShift(courierId)).thenThrow(new ShiftEndNotAllowedException(
                "Cannot end shift: the courier still has 2 active parcel(s). Complete or reassign them first."));

        mockMvc.perform(post("/api/v1/dispatch/couriers/shifts/{courierId}/end", courierId).with(courier(courierId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Shift Conflict"))
                .andExpect(jsonPath("$.message").value(
                        "Cannot end shift: the courier still has 2 active parcel(s). Complete or reassign them first."));
    }

    @Test
    @DisplayName("A unique-key violation returns 409 Data Conflict without database details")
    void testStartShift_UniqueViolationIsSafeConflict() throws Exception {
        when(courierShiftService.startShift(any(StartShiftRequest.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement [duplicate key value violates unique constraint \"courier_shifts_courier_id_key\"]",
                new SQLException("duplicate key value violates unique constraint", "23505")));

        mockMvc.perform(post("/api/v1/dispatch/couriers/shifts/start").with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(startShiftRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Data Conflict"))
                .andExpect(jsonPath("$.message").value("The request conflicts with an existing record. Refresh and retry."))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("duplicate key", "courier_shifts", "constraint"));
    }

    @Test
    @DisplayName("Other integrity violations are not misreported as conflicts: generic 500, no details")
    void testStartShift_OtherIntegrityViolationIsGenericServerError() throws Exception {
        when(courierShiftService.startShift(any(StartShiftRequest.class))).thenThrow(new DataIntegrityViolationException(
                "null value in column \"courier_name\" violates not-null constraint",
                new SQLException("null value in column", "23502")));

        mockMvc.perform(post("/api/v1/dispatch/couriers/shifts/start").with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(startShiftRequest())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("courier_name", "not-null"));
    }

    private StartShiftRequest startShiftRequest() {
        return new StartShiftRequest(UUID.randomUUID(), "Shift Courier", null, SriLankanDistrict.COLOMBO);
    }
}
