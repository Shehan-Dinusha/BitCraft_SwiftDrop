package com.swiftdrop.delivery.controller;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.dto.DeliverySessionResponse;
import com.swiftdrop.delivery.exception.GlobalExceptionHandler;
import com.swiftdrop.delivery.service.DeliveryExecutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeliveryExecutionController.class)
@Import(GlobalExceptionHandler.class)
class TodayDeliveriesEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeliveryExecutionService deliveryService;

    @Test
    @DisplayName("GET /api/v1/deliveries/me without authentication returns 401 Unauthorized")
    void testGetMyDeliveriesUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/deliveries/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("GET /api/v1/deliveries/me with customer role returns 403 Forbidden")
    void testGetMyDeliveriesForbiddenForNonCourier() throws Exception {
        UUID customerId = UUID.randomUUID();

        when(deliveryService.getMyDeliveries(any(CallerContext.class), any()))
                .thenThrow(new ForbiddenException("Caller role CUSTOMER is not authorized for courier delivery operations"));

        mockMvc.perform(get("/api/v1/deliveries/me")
                        .header(JwtHeaderConstants.X_USER_ID, customerId.toString())
                        .header(JwtHeaderConstants.X_USER_ROLE, UserRole.CUSTOMER.name()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("GET /api/v1/deliveries/me returns courier's deliveries for today")
    void testGetMyDeliveriesSuccess() throws Exception {
        UUID courierId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();

        DeliverySession entity = new DeliverySession();
        entity.setDeliveryId(deliveryId);
        entity.setOrderId(UUID.randomUUID());
        entity.setTrackingNumber("SD-COL-998877");
        entity.setCourierId(courierId);
        entity.setCourierName("Kasun Perera");
        entity.setPickupDistrict(SriLankanDistrict.COLOMBO);
        entity.setDeliveryDistrict(SriLankanDistrict.GAMPAHA);
        entity.setCurrentState(DeliveryState.ASSIGNED);
        entity.setPreviousState(DeliveryState.CREATED);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        DeliverySessionResponse delivery = DeliverySessionResponse.fromEntity(entity);

        when(deliveryService.getMyDeliveries(any(CallerContext.class), any()))
                .thenReturn(List.of(delivery));

        mockMvc.perform(get("/api/v1/deliveries/me")
                        .header(JwtHeaderConstants.X_USER_ID, courierId.toString())
                        .header(JwtHeaderConstants.X_USER_ROLE, UserRole.COURIER.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].deliveryId").value(deliveryId.toString()))
                .andExpect(jsonPath("$.data[0].courierId").value(courierId.toString()))
                .andExpect(jsonPath("$.data[0].trackingNumber").value("SD-COL-998877"))
                .andExpect(jsonPath("$.data[0].currentState").value("ASSIGNED"));
    }

    @Test
    @DisplayName("GET /api/v1/deliveries/me?date=2026-11-12 passes date parameter to service")
    void testGetMyDeliveriesWithSpecificDate() throws Exception {
        UUID courierId = UUID.randomUUID();

        when(deliveryService.getMyDeliveries(any(CallerContext.class), eq("2026-11-12")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/deliveries/me")
                        .param("date", "2026-11-12")
                        .header(JwtHeaderConstants.X_USER_ID, courierId.toString())
                        .header(JwtHeaderConstants.X_USER_ROLE, UserRole.COURIER.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
