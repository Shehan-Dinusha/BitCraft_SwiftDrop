package com.swiftdrop.order.controller;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.dto.InternalTrackingView;
import com.swiftdrop.order.exception.OrderNotFoundException;
import com.swiftdrop.order.service.InternalOrderService;
import com.swiftdrop.order.service.InternalTrackingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalOrderController.class)
class InternalOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InternalTrackingService internalTrackingService;

    @MockitoBean
    private InternalOrderService internalOrderService;

    @Test
    @DisplayName("Returns the tracking view without any user headers")
    void returnsTrackingView() throws Exception {
        when(internalTrackingService.getTrackingView("SD-7K4M-9QX2")).thenReturn(new InternalTrackingView(
                "SD-7K4M-9QX2",
                PublicOrderStatus.PICKED_UP,
                List.of(new InternalTrackingView.HistoryEntry(PublicOrderStatus.CREATED, Instant.parse("2026-10-30T08:00:00Z")),
                        new InternalTrackingView.HistoryEntry(PublicOrderStatus.PICKED_UP, Instant.parse("2026-10-30T10:00:00Z"))),
                "Colombo 03", SriLankanDistrict.COLOMBO, "Kandy", SriLankanDistrict.KANDY));

        mockMvc.perform(get("/internal/v1/orders/tracking/{trackingNumber}", "SD-7K4M-9QX2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingNumber").value("SD-7K4M-9QX2"))
                .andExpect(jsonPath("$.data.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.data.history[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data.history[1].status").value("PICKED_UP"))
                .andExpect(jsonPath("$.data.pickupDistrict").value("COLOMBO"))
                .andExpect(jsonPath("$.data.deliveryCity").value("Kandy"));
    }

    @Test
    @DisplayName("An unknown tracking number returns 404 with the safe error shape")
    void unknownNumberReturns404() throws Exception {
        when(internalTrackingService.getTrackingView("SD-0000-0000")).thenThrow(new OrderNotFoundException("Order not found"));

        String body = mockMvc.perform(get("/internal/v1/orders/tracking/{trackingNumber}", "SD-0000-0000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("SD-0000-0000");
    }

    @ParameterizedTest(name = "[{index}] malformed number is rejected")
    @ValueSource(strings = {"SD-123", "sd-7k4m-9qx2", "SD-7K4M-9QX2X", "XX-7K4M-9QX2", "SD-7K4M_9QX2", "SD-%3Cscript%3E"})
    void malformedNumberReturns400(String number) throws Exception {
        String body = mockMvc.perform(get("/internal/v1/orders/tracking/{trackingNumber}", number))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(number).doesNotContain("script");
        verify(internalTrackingService, never()).getTrackingView(org.mockito.ArgumentMatchers.anyString());
    }
}
