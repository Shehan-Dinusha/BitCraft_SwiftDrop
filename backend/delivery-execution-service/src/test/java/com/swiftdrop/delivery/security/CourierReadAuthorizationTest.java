package com.swiftdrop.delivery.security;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.delivery.controller.DeliveryExecutionController;
import com.swiftdrop.delivery.controller.ProofOfDeliveryController;
import com.swiftdrop.delivery.dto.DeliverySessionResponse;
import com.swiftdrop.delivery.exception.GlobalExceptionHandler;
import com.swiftdrop.delivery.service.DeliveryExecutionService;
import com.swiftdrop.delivery.service.ProofOfDeliveryStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {DeliveryExecutionController.class, ProofOfDeliveryController.class})
@Import(GlobalExceptionHandler.class)
class CourierReadAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeliveryExecutionService deliveryService;

    @MockitoBean
    private ProofOfDeliveryStorageService storageService;

    @Test
    @DisplayName("Courier A reading Courier B delivery by ID returns 403 Forbidden")
    void testCourierCannotReadOtherCourierDeliveryById() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        UUID courierA = UUID.randomUUID();

        when(deliveryService.getDeliveryById(eq(deliveryId), any()))
                .thenThrow(new ForbiddenException("Courier does not own this delivery session"));

        mockMvc.perform(get("/api/v1/deliveries/" + deliveryId)
                        .header(JwtHeaderConstants.X_USER_ID, courierA.toString())
                        .header(JwtHeaderConstants.X_USER_ROLE, UserRole.COURIER.name()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Courier does not own this delivery session"));
    }

    @Test
    @DisplayName("Courier A reading Courier B deliveries list returns 403 Forbidden")
    void testCourierCannotListOtherCourierDeliveries() throws Exception {
        UUID courierA = UUID.randomUUID();
        UUID courierB = UUID.randomUUID();

        when(deliveryService.getDeliveriesByCourier(eq(courierB), any(), any()))
                .thenThrow(new ForbiddenException("Courier does not own this delivery resource"));

        mockMvc.perform(get("/api/v1/deliveries/courier/" + courierB)
                        .header(JwtHeaderConstants.X_USER_ID, courierA.toString())
                        .header(JwtHeaderConstants.X_USER_ROLE, UserRole.COURIER.name()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("Courier A reading Courier B POD returns 403 Forbidden")
    void testCourierCannotReadOtherCourierPod() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        UUID courierA = UUID.randomUUID();

        doThrow(new ForbiddenException("Courier does not own this delivery resource"))
                .when(deliveryService).assertCanAccess(eq(deliveryId), any());

        mockMvc.perform(get("/api/v1/deliveries/" + deliveryId + "/proof-of-delivery")
                        .header(JwtHeaderConstants.X_USER_ID, courierA.toString())
                        .header(JwtHeaderConstants.X_USER_ROLE, UserRole.COURIER.name()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("Courier reading their own delivery succeeds with 200 OK")
    void testCourierCanReadOwnDelivery() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        UUID courierA = UUID.randomUUID();

        DeliverySessionResponse response = new DeliverySessionResponse(
                deliveryId, UUID.randomUUID(), "SD-COL-99999", courierA, "Courier A",
                SriLankanDistrict.COLOMBO, SriLankanDistrict.GAMPAHA, DeliveryState.IN_TRANSIT,
                DeliveryState.PICKED_UP, null, 0, Instant.now(), null,
                BigDecimal.valueOf(6.9271), BigDecimal.valueOf(79.8612), Instant.now(), Instant.now()
        );

        when(deliveryService.getDeliveryById(eq(deliveryId), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/deliveries/" + deliveryId)
                        .header(JwtHeaderConstants.X_USER_ID, courierA.toString())
                        .header(JwtHeaderConstants.X_USER_ROLE, UserRole.COURIER.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deliveryId").value(deliveryId.toString()));
    }
}

