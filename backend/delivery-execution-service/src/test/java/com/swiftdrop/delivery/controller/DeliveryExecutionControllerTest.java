package com.swiftdrop.delivery.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.dto.DeliverySessionResponse;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.service.DeliveryExecutionService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryExecutionController.class)
class DeliveryExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DeliveryExecutionService deliveryService;

    @Test
    @DisplayName("GET /api/v1/deliveries/health returns UP")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/deliveries/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("UP"));
    }

    @Test
    @DisplayName("GET /api/v1/deliveries/{deliveryId} returns delivery details")
    void testGetDeliveryById() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        DeliverySessionResponse response = new DeliverySessionResponse(
                deliveryId,
                UUID.randomUUID(),
                "SD-COL-11111",
                UUID.randomUUID(),
                "Sunil Perera",
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.GAMPAHA,
                DeliveryState.IN_TRANSIT,
                DeliveryState.PICKED_UP,
                null,
                0,
                Instant.now(),
                null,
                BigDecimal.valueOf(6.9271),
                BigDecimal.valueOf(79.8612),
                Instant.now(),
                Instant.now()
        );

        when(deliveryService.getDeliveryById(deliveryId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/deliveries/" + deliveryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deliveryId").value(deliveryId.toString()))
                .andExpect(jsonPath("$.data.currentState").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.data.courierName").value("Sunil Perera"));
    }

    @Test
    @DisplayName("POST /api/v1/deliveries/{deliveryId}/transition transitions FSM")
    void testTransition() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        TransitionDeliveryRequest request = new TransitionDeliveryRequest(
                DeliveryState.PICKED_UP,
                "Collected from warehouse",
                BigDecimal.valueOf(6.9271),
                BigDecimal.valueOf(79.8612),
                null,
                "COURIER"
        );

        DeliverySession session = new DeliverySession();
        session.setDeliveryId(deliveryId);
        session.setOrderId(UUID.randomUUID());
        session.setTrackingNumber("SD-COL-11111");
        session.setCurrentState(DeliveryState.PICKED_UP);
        session.setPreviousState(DeliveryState.ASSIGNED);

        when(deliveryService.transition(eq(deliveryId), any(TransitionDeliveryRequest.class))).thenReturn(session);

        mockMvc.perform(post("/api/v1/deliveries/" + deliveryId + "/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentState").value("PICKED_UP"));
    }
}
