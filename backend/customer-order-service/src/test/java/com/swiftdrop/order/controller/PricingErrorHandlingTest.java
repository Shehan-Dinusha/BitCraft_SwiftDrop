package com.swiftdrop.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.dto.FeeEstimateRequest;
import com.swiftdrop.order.exception.PricingRequestRejectedException;
import com.swiftdrop.order.exception.PricingUnavailableException;
import com.swiftdrop.order.service.CustomerOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(OrderController.class)
class PricingErrorHandlingTest {

    private static final String DOWNSTREAM_DETAIL =
            "http://pricing-zone-service:8083/api/v1/pricing/quote returned secret-downstream-body";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerOrderService customerOrderService;

    private String estimateBody() throws Exception {
        return objectMapper.writeValueAsString(FeeEstimateRequest.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .actualWeightKg(new BigDecimal("1.5"))
                .build());
    }

    @Test
    @DisplayName("Pricing outage returns 503 with a fixed message and no downstream details")
    void pricingUnavailableReturns503(CapturedOutput output) throws Exception {
        when(customerOrderService.calculateEstimate(any()))
                .thenThrow(new PricingUnavailableException(new RuntimeException(DOWNSTREAM_DETAIL)));

        String body = mockMvc.perform(post("/api/v1/orders/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(estimateBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Pricing service is temporarily unavailable"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("pricing-zone-service", "8083", "secret-downstream-body", "/api/v1/pricing");
        assertThat(output.getAll()).doesNotContain("pricing-zone-service", "secret-downstream-body");
    }

    @Test
    @DisplayName("A pricing 4xx on a validated request returns 502 with a generic message")
    void pricingRejectionReturns502() throws Exception {
        when(customerOrderService.calculateEstimate(any())).thenThrow(new PricingRequestRejectedException());

        String body = mockMvc.perform(post("/api/v1/orders/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(estimateBody()))
                .andExpect(status().isBadGateway())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("pricing-zone-service", "8083");
    }
}
