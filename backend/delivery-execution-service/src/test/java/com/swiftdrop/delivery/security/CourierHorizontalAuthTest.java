package com.swiftdrop.delivery.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.delivery.controller.DeliveryExecutionController;
import com.swiftdrop.delivery.controller.ProofOfDeliveryController;
import com.swiftdrop.delivery.dto.CourierLocationUpdatePayload;
import com.swiftdrop.delivery.dto.ProofOfDeliveryUploadRequest;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.exception.GlobalExceptionHandler;
import com.swiftdrop.delivery.service.DeliveryExecutionService;
import com.swiftdrop.delivery.service.ProofOfDeliveryStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {DeliveryExecutionController.class, ProofOfDeliveryController.class})
@Import(GlobalExceptionHandler.class)
class CourierHorizontalAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DeliveryExecutionService deliveryService;

    @MockitoBean
    private ProofOfDeliveryStorageService storageService;

    @Test
    @DisplayName("Courier A reading Courier B delivery session returns 403 Forbidden")
    void testCourierACannotReadCourierBDelivery() throws Exception {
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
    @DisplayName("Courier A transitioning Courier B delivery returns 403 Forbidden")
    void testCourierACannotTransitionCourierBDelivery() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        UUID courierA = UUID.randomUUID();

        when(deliveryService.transition(eq(deliveryId), any(TransitionDeliveryRequest.class), any()))
                .thenThrow(new ForbiddenException("Courier does not own this delivery session"));

        TransitionDeliveryRequest request = new TransitionDeliveryRequest(
                DeliveryState.PICKED_UP,
                "Picked up parcel",
                BigDecimal.valueOf(6.9271),
                BigDecimal.valueOf(79.8612),
                null,
                "COURIER"
        );

        mockMvc.perform(post("/api/v1/deliveries/" + deliveryId + "/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(JwtHeaderConstants.X_USER_ID, courierA.toString())
                        .header(JwtHeaderConstants.X_USER_ROLE, UserRole.COURIER.name())
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Courier does not own this delivery session"));
    }

    @Test
    @DisplayName("Courier A recording location for Courier B delivery returns 403 Forbidden")
    void testCourierACannotRecordLocationForCourierBDelivery() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        UUID courierA = UUID.randomUUID();

        when(deliveryService.recordLocation(eq(deliveryId), any(CourierLocationUpdatePayload.class), any()))
                .thenThrow(new ForbiddenException("Courier does not own this delivery session"));

        CourierLocationUpdatePayload payload = new CourierLocationUpdatePayload(
                6.9271, 79.8612, 45.0, 25.0, 5.0
        );

        mockMvc.perform(post("/api/v1/deliveries/" + deliveryId + "/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(JwtHeaderConstants.X_USER_ID, courierA.toString())
                        .header(JwtHeaderConstants.X_USER_ROLE, UserRole.COURIER.name())
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("Courier A uploading POD for Courier B delivery returns 403 Forbidden")
    void testCourierACannotUploadPodForCourierBDelivery() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        UUID courierA = UUID.randomUUID();

        doThrow(new ForbiddenException("Courier does not own this delivery session"))
                .when(deliveryService).assertCanAccess(eq(deliveryId), any());

        ProofOfDeliveryUploadRequest request = new ProofOfDeliveryUploadRequest(
                "https://storage.swiftdrop.lk/pod/photo.jpg",
                "signature-base64",
                "123456"
        );

        mockMvc.perform(post("/api/v1/deliveries/" + deliveryId + "/proof-of-delivery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(JwtHeaderConstants.X_USER_ID, courierA.toString())
                        .header(JwtHeaderConstants.X_USER_ROLE, UserRole.COURIER.name())
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("Courier A verifying POD OTP for Courier B delivery returns 403 Forbidden")
    void testCourierACannotVerifyPodForCourierBDelivery() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        UUID courierA = UUID.randomUUID();

        doThrow(new ForbiddenException("Courier does not own this delivery session"))
                .when(deliveryService).assertCanAccess(eq(deliveryId), any());

        mockMvc.perform(post("/api/v1/deliveries/" + deliveryId + "/proof-of-delivery/verify")
                        .header(JwtHeaderConstants.X_USER_ID, courierA.toString())
                        .header(JwtHeaderConstants.X_USER_ROLE, UserRole.COURIER.name())
                        .param("otp", "123456"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("Courier B transitioning own delivery succeeds with 200 OK")
    void testCourierBCanTransitionOwnDelivery() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        UUID courierB = UUID.randomUUID();

        DeliverySession session = new DeliverySession();
        session.setDeliveryId(deliveryId);
        session.setCourierId(courierB);
        session.setCurrentState(DeliveryState.PICKED_UP);

        when(deliveryService.transition(eq(deliveryId), any(TransitionDeliveryRequest.class), any()))
                .thenReturn(session);

        TransitionDeliveryRequest request = new TransitionDeliveryRequest(
                DeliveryState.PICKED_UP,
                "Picked up parcel",
                BigDecimal.valueOf(6.9271),
                BigDecimal.valueOf(79.8612),
                null,
                "COURIER"
        );

        mockMvc.perform(post("/api/v1/deliveries/" + deliveryId + "/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(JwtHeaderConstants.X_USER_ID, courierB.toString())
                        .header(JwtHeaderConstants.X_USER_ROLE, UserRole.COURIER.name())
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentState").value("PICKED_UP"));
    }
}

